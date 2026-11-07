package wraphex.slighte.hook

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.highcapable.yukihookapi.hook.log.YLog
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.pow
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.makeAccessible

/**
 * Levels accepted by Settings.System.SCREEN_BRIGHTNESS.
 *
 * The floor is 1, not config_screenBrightnessSettingMinimum (10 on the tested Redmi K60):
 * that resource is the minimum of the brightness *slider* in the Settings UI, not the dimmest
 * the panel can go. The display reports mBrightnessMinimum=9.16e-4, so 1/255 is still a valid
 * level and is far dimmer than 10.
 */
private const val MIN_SETTING = 1
private const val MAX_SETTING = 255
private const val GAMMA = 2.4f

/** Smallest level change that is visible on the panel. */
private const val SENT_EPSILON = 0.2f

/** Maps a 0f..1f slider position to a continuous brightness level (1f..255f). */
internal fun brightnessSetting(percent: Float): Float {
    val curved = percent.coerceIn(0f, 1f).pow(GAMMA)
    return MIN_SETTING + (MAX_SETTING - MIN_SETTING) * curved
}

/** Maps a 0f..1f slider position to the integer written to Settings.System. */
internal fun brightnessValue(percent: Float): Int = levelOf(brightnessSetting(percent))

/** Truncates a continuous level to a Setting value the framework accepts. */
internal fun levelOf(setting: Float): Int =
    setting.toInt().coerceIn(MIN_SETTING, MAX_SETTING)

/** A gesture counts as horizontal once it clearly moves sideways, whatever its speed. */
internal fun isHorizontalSwipe(dx: Float, dy: Float, slop: Float): Boolean =
    dx > slop && dx > dy * 2

/** False for changes too small to see on the panel, which would only add binder traffic. */
internal fun needsSend(previous: Float?, next: Float): Boolean =
    previous == null || abs(next - previous) >= SENT_EPSILON

internal class BrightnessHelper(context: Context) : View.OnTouchListener {

    companion object {
        // Resolved once per process: the signature differs across Android versions.
        private val temporaryBrightnessMethod: Method? by lazy {
            classOf<DisplayManager>().declaredMethods
                .firstOrNull { it.name == "setTemporaryBrightness" && it.parameterCount in 1..2 }
                ?.apply { makeAccessible() }
                ?.also { YLog.info("setTemporaryBrightness found: ${it.parameterTypes.toList()}") }
                ?: null.also {
                    YLog.error("DisplayManager.setTemporaryBrightness not found, swipe is inert")
                }
        }
    }

    private var startX = 0f
    private var startY = 0f
    private val swipeSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    /**
     * Brightness actually pushed to the framework. Null means the gesture is not claimed yet:
     * the first applied move always sets it, so it doubles as the "this is our swipe" flag.
     */
    private var lastSent: Float? = null
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val contentResolver = context.contentResolver

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                lastSent = null
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (lastSent == null) {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (!isHorizontalSwipe(dx, dy, swipeSlop)) return false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                applyMove(v, event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val sent = lastSent ?: return false
                lastSent = null
                v.parent?.requestDisallowInterceptTouchEvent(false)
                persist(sent)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasClaimed = lastSent != null
                lastSent = null
                if (wasClaimed) v.parent?.requestDisallowInterceptTouchEvent(false)
                return wasClaimed
            }
        }

        return false
    }

    private fun applyMove(v: View, event: MotionEvent) {
        // Absolute mapping on purpose: the finger position is the brightness percentage,
        // so a tap anywhere on the bar jumps straight to that level.
        val setting = brightnessSetting((event.x / v.width).coerceIn(0f, 1f))
        if (!needsSend(lastSent, setting)) return

        lastSent = setting
        setTemporary(setting)
    }

    private fun setTemporary(setting: Float) {
        val dm = displayManager ?: return

        val method = temporaryBrightnessMethod ?: return
        // The float variant expects the 0f..1f setting domain, the int variant the raw range.
        val scaled = if (method.parameterTypes.last() == classOf<Float>()) {
            (setting / MAX_SETTING).coerceIn(0f, 1f)
        } else {
            levelOf(setting)
        }
        val args: Array<Any?> =
            if (method.parameterCount == 2) arrayOf(0, scaled) else arrayOf(scaled)

        runCatching {
            method.invoke(dm, *args)
        }.onFailure { YLog.warn("setTemporaryBrightness failed at $setting", it) }
    }

    private fun persist(setting: Float) {
        val value = levelOf(setting)
        runCatching {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        }.onFailure { YLog.warn("failed to persist brightness $value", it) }
    }
}
