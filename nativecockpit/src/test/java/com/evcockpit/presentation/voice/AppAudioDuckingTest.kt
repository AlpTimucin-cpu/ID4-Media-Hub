package com.evcockpit.presentation.voice

import org.junit.Assert.*
import org.junit.Test

class AppAudioDuckingTest {
    @Test fun concurrentWelcomeAndNavigationDoNotUnduckEachOther() {
        val manager = AppAudioDucking()
        assertFalse(manager.ducked.value)
        manager.begin("welcome")
        assertTrue(manager.ducked.value)
        manager.begin("navigation:42")
        manager.end("welcome")
        assertTrue(manager.ducked.value)
        manager.end("navigation:42")
        assertFalse(manager.ducked.value)
        manager.end("navigation:42")
        assertFalse(manager.ducked.value)
    }
}
