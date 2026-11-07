package wraphex.slighte.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessValueTest {

    @Test
    fun `full width is the maximum level`() {
        assertEquals(255, brightnessValue(1f))
    }

    @Test
    fun `the left edge is the dimmest level, not the slider minimum`() {
        // config_screenBrightnessSettingMinimum (10 here) is the Settings *slider* floor,
        // not the dimmest the panel can go. 1 must remain reachable.
        assertEquals(1, brightnessValue(0f))
        assertEquals(1, brightnessValue(0.01f))
        assertEquals(1, brightnessValue(-1f))
    }

    @Test
    fun `out of range positions are clamped, not wrapped`() {
        assertEquals(255, brightnessValue(2f))
    }

    @Test
    fun `the integer curve is monotonic`() {
        var previous = brightnessValue(0f)
        for (step in 1..100) {
            val current = brightnessValue(step / 100f)
            assertTrue("level dropped at $step%: $previous -> $current", current >= previous)
            previous = current
        }
    }

    @Test
    fun `the float curve never stalls across a full screen width`() {
        // The gesture path uses the float value. If adjacent pixels mapped to the same
        // value the low end would step visibly, which is the bug this guards.
        var previous = brightnessSetting(0f)
        for (step in 1..1440) {
            val current = brightnessSetting(step / 1440f)
            assertTrue("float curve stalled at step $step", current > previous)
            previous = current
        }
    }

    @Test
    fun `mid position is well below the middle level`() {
        // Gamma keeps the low end fine-grained on purpose.
        assertTrue(brightnessValue(0.5f) < 128)
    }
}
