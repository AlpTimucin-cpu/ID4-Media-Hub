package com.evcockpit.presentation.sound

import org.junit.Assert.*
import org.junit.Test

class EngineMotionPolicyTest {
    @Test fun noRealSpeedMustBeSilent() {
        assertEquals(
            EngineMotionPolicy.Mode.SILENT,
            EngineMotionPolicy.decide(null, null, 0.0).mode
        )
        assertEquals(
            EngineMotionPolicy.Mode.IDLE,
            EngineMotionPolicy.decide(4.5, 3.0, 1.0).mode
        )
    }

    @Test fun accelerationChangesLayerAndPitch() {
        val accel = EngineMotionPolicy.decide(55.0, 45.0, 2.0)
        assertEquals(EngineMotionPolicy.Mode.ACCELERATING, accel.mode)
        assertTrue(accel.pitch > 0.9f)
        val roar = EngineMotionPolicy.decide(65.0, 42.0, 1.0)
        assertEquals(1.9f, roar.pitch, 0.0001f)
        assertEquals(EngineMotionPolicy.Mode.IDLE,
            EngineMotionPolicy.decide(60.0, 65.0, 2.0).mode)
    }

    @Test fun invalidTimeDoesNotProduceArtificialRoar() {
        val m = EngineMotionPolicy.decide(80.0, 20.0, 0.0)
        assertEquals(EngineMotionPolicy.Mode.IDLE, m.mode)
        assertTrue(m.pitch < 1.9f)
    }
}
