package tr.id4.mediahub.nativecockpit.presentation.voice

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM policy simulation only: Android's real AudioManager and Netflix/Mola
 * response require device tests, not assertions about third-party apps.
 */
class AudioFocusLeaseTest {
    @Test fun cooperativeMediaDucksDuringNavigationAndRestoresAfterSpeech() {
        var mediaVolume = 1.0
        var requests = 0
        var releases = 0
        val focus = AudioFocusLease(
            requestFocus = {
                requests++
                mediaVolume = 0.25 // fake media player's onAudioFocusChange
                true
            },
            abandonFocus = {
                releases++
                mediaVolume = 1.0
            }
        )
        assertTrue(focus.acquire())
        assertTrue(focus.shouldStopAfterLoss())
        assertEquals(0.25, mediaVolume, 0.0)
        assertTrue(focus.acquire()) // repeated requests do not stack
        assertEquals(1, requests)
        focus.release()
        assertEquals(1, releases)
        assertEquals(1.0, mediaVolume, 0.0)
        assertFalse(focus.shouldStopAfterLoss())
        focus.close()
        assertEquals(1, releases)
    }

    @Test fun focusLostCallbackQueuedBeforeCloseIsIgnored() {
        var abandoned = 0
        val focus = AudioFocusLease(
            requestFocus = { true },
            abandonFocus = { abandoned++ }
        )
        assertTrue(focus.acquire())
        focus.close() // Activity / ViewModel destroyed
        assertFalse(focus.shouldStopAfterLoss())
        assertFalse(focus.acquire())
        focus.close()
        assertEquals(1, abandoned)
    }

    @Test fun focusDeniedDoesNotModifyMediaOrClaimOwnership() {
        var mediaVolume = 1.0
        var releases = 0
        val focus = AudioFocusLease(
            requestFocus = { false },
            abandonFocus = {
                mediaVolume = 1.0
                releases++
            }
        )
        assertFalse(focus.acquire())
        assertFalse(focus.shouldStopAfterLoss())
        focus.close()
        assertEquals(1.0, mediaVolume, 0.0)
        assertEquals(0, releases)
    }
}
