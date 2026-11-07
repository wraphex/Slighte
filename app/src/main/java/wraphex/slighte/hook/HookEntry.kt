package wraphex.slighte.hook

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.extension.kavaref.toClassOrNull
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import java.util.WeakHashMap

@YukiHookLibXposedEntry(
    targetApiVersion = 102,
    scope = ["com.android.systemui"],
    staticScope = true
)
object HookEntry : YukiHookXposedModule {

    private const val STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView"

    override fun onHook() = encase {
        loadApp("com.android.systemui") {
            val statusBarView = STATUS_BAR_VIEW.toClassOrNull()
            if (statusBarView == null) {
                YLog.error("$STATUS_BAR_VIEW not found, brightness swipe disabled")
                return@loadApp
            }

            // One helper per view. Weak keys: the view is recreated on configuration changes.
            val helpers = WeakHashMap<View, BrightnessHelper>()

            statusBarView.resolve()
                .firstMethodOrNull { name = "onAttachedToWindow" }
                ?.hook {
                    after {
                        val view = instanceOrNull<View>() ?: return@after
                        val helper = helpers.getOrPut(view) { BrightnessHelper(view.context) }
                        view.setOnTouchListener { v, event -> helper.onTouch(v, event) }
                    }
                } ?: YLog.error("PhoneStatusBarView#onAttachedToWindow not found, swipe disabled")
        }
    }
}
