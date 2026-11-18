package com.example.splendit.domain

import kotlin.math.pow

/** Wheel maths. Angles are degrees clockwise from 12 o'clock, where the pointer sits. */
object Wheel {
    data class Segment(val id: String, val startAngle: Float, val sweep: Float)

    fun segments(ids: List<String>, weights: List<Double>): List<Segment> {
        val total = weights.sum()
        var acc = 0f
        return ids.mapIndexed { i, id ->
            val sweep = (weights[i] / total * 360).toFloat()
            Segment(id, acc, sweep).also { acc += sweep }
        }
    }

    /** Weighted draw. [roll] is uniform in [0, 1). */
    fun pickIndex(weights: List<Double>, roll: Double): Int {
        var r = roll * weights.sum()
        var i = 0
        while (r > weights[i] && i < weights.lastIndex) {
            r -= weights[i]
            i++
        }
        return i
    }

    /**
     * Rotation that lands segment [index] under the pointer after seven full turns from [base].
     * [jitter] in [-0.5, 0.5] keeps the stop off the exact centre of the slice.
     */
    fun targetRotation(base: Float, weights: List<Double>, index: Int, jitter: Double): Float {
        val segment = segments(weights.indices.map { it.toString() }, weights)[index]
        val centre =
            segment.startAngle + segment.sweep / 2 + (jitter * segment.sweep * 0.7).toFloat()
        return base - (base % 360) + 360 * 7 + (360 - centre)
    }

    /** Jackpot odds: rating squared, so faster payers get noticeably bigger slices. */
    fun jackpotWeights(ratings: List<Double>): List<Double> = ratings.map { it.pow(2) }
}
