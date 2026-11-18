package com.example.splendit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelTest {
    @Test
    fun segmentsCoverTheCircle() {
        val s = Wheel.segments(listOf("a", "b", "c"), listOf(1.0, 2.0, 1.0))
        assertEquals(0f, s[0].startAngle, 1e-3f)
        assertEquals(180f, s[1].sweep, 1e-3f)
        assertEquals(360f, s.last().startAngle + s.last().sweep, 1e-3f)
    }

    @Test
    fun pickIndexFollowsWeights() {
        val w = listOf(1.0, 2.0, 1.0)
        assertEquals(0, Wheel.pickIndex(w, 0.0))
        assertEquals(1, Wheel.pickIndex(w, 0.5))
        assertEquals(2, Wheel.pickIndex(w, 0.99))
    }

    @Test
    fun targetRotationPutsChosenSliceUnderPointer() {
        val w = listOf(1.0, 1.0, 1.0, 1.0)
        val rot = Wheel.targetRotation(base = 123f, weights = w, index = 2, jitter = 0.0)
        // Slice 2 is centred at 225°; after rotating, that point must sit at 0° (12 o'clock).
        assertEquals(0f, (225f + rot) % 360f, 1e-3f)
        assertEquals(true, rot >= 360f * 7)
    }

    @Test
    fun jackpotWeightsSquareRatings() {
        assertEquals(listOf(25.0, 9.0), Wheel.jackpotWeights(listOf(5.0, 3.0)))
    }
}
