package wraphex.slighte.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeLogicTest {

    private val slop = 24f

    @Test
    fun `a tiny move never claims the gesture`() {
        assertFalse(isHorizontalSwipe(dx = 10f, dy = 0f, slop = slop))
    }

    @Test
    fun `a diagonal drag stays with the status bar`() {
        // 60px sideways but 40px down: pulling the shade must still work.
        assertFalse(isHorizontalSwipe(dx = 60f, dy = 40f, slop = slop))
    }

    @Test
    fun `a clearly sideways drag claims the gesture`() {
        assertTrue(isHorizontalSwipe(dx = 60f, dy = 10f, slop = slop))
    }

    @Test
    fun `the first level of a gesture is always sent`() {
        assertTrue(needsSend(previous = null, next = 30f))
    }

    @Test
    fun `an invisible level change is dropped`() {
        assertFalse(needsSend(previous = 30f, next = 30.1f))
    }

    @Test
    fun `a visible level change is sent`() {
        assertTrue(needsSend(previous = 30f, next = 30.5f))
    }

    @Test
    fun `dragging down sends a new level too`() {
        assertTrue(needsSend(previous = 200f, next = 150f))
    }
}
