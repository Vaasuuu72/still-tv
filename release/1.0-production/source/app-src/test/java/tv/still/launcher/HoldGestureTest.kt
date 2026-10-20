package tv.still.launcher
import org.junit.Assert.*
import org.junit.Test
class HoldGestureTest {
    @Test fun shortPressIsAClick() {
        val gesture = HoldGesture()
        assertTrue(gesture.down(repeatCount = 0))
        assertEquals(HoldGesture.Release.CLICK, gesture.up())
    }
    @Test fun releaseAfterHoldFiredDoesNothing() {
        val gesture = HoldGesture()
        gesture.down(repeatCount = 0)
        assertTrue(gesture.hold())
        assertEquals(HoldGesture.Release.NONE, gesture.up())
    }
    @Test fun keyRepeatsWhileHeldDoNotRestartTheGesture() {
        val gesture = HoldGesture()
        assertTrue(gesture.down(repeatCount = 0))
        assertFalse(gesture.down(repeatCount = 1))
        assertFalse(gesture.down(repeatCount = 5))
        assertEquals(HoldGesture.Release.CLICK, gesture.up())
    }
    @Test fun holdFiresOnlyOncePerPress() {
        val gesture = HoldGesture()
        gesture.down(repeatCount = 0)
        assertTrue(gesture.hold())
        assertFalse(gesture.hold())
    }
    @Test fun releaseWithoutPressDoesNothing() {
        assertEquals(HoldGesture.Release.NONE, HoldGesture().up())
    }
    @Test fun cancelledPressNeitherClicksNorHolds() {
        val gesture = HoldGesture()
        gesture.down(repeatCount = 0)
        gesture.cancel()
        assertFalse(gesture.hold())
        assertEquals(HoldGesture.Release.NONE, gesture.up())
    }
    @Test fun newPressStartsAgainWhenTheReleaseWentToTheDialog() {
        val gesture = HoldGesture()
        gesture.down(repeatCount = 0)
        gesture.hold()                       // dialog opened; its window receives the key-up
        assertTrue(gesture.down(repeatCount = 0))
        assertEquals(HoldGesture.Release.CLICK, gesture.up())
    }
    @Test fun holdThresholdIsShorterThanTheSystemDefault() {
        assertTrue(HoldGesture.HOLD_MS in 150L..350L)
    }
}
