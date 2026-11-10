package com.example.splendit.domain

import com.example.splendit.data.model.ME
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.SplitMode
import kotlin.math.abs

/**
 * The "New expense" form. Immutable: every edit returns a new draft, so the ViewModel can hold
 * it in a StateFlow and the split rules can be unit tested without Compose.
 */
data class ExpenseDraft(
    val title: String = "",
    val amount: String = "",
    val participants: List<String>,
    val method: PayMethod,
    val split: SplitMode = SplitMode.EQUAL,
    /** Fraction per participant; only used when [split] is not EQUAL. */
    val custom: Map<String, Double>? = null,
    /** Raw text the user is typing into a split row, kept until the field loses focus. */
    val typed: Map<String, String> = emptyMap(),
    /** Participants whose share "me" has taken over. */
    val covered: Set<String> = emptySet()
) {
    val amountValue: Double get() = amount.replace(',', '.').toDoubleOrNull() ?: 0.0

    fun equalFractions(): Map<String, Double> {
        val n = participants.size.coerceAtLeast(1)
        return participants.associateWith { 1.0 / n }
    }

    /** Falls back to an equal split while the custom map does not match the participants. */
    fun fractions(): Map<String, Double> {
        val c = custom
        if (split == SplitMode.EQUAL || c == null) return equalFractions()
        val matches = c.size == participants.size && participants.all { it in c }
        return if (matches) c else equalFractions()
    }

    val assigned: Double get() = fractions().let { f -> participants.sumOf { f[it] ?: 0.0 } }

    val isValid: Boolean
        get() = amountValue > 0 &&
            participants.size >= 2 &&
            title.isNotBlank() &&
            abs(assigned - 1.0) <= 0.001

    /**
     * Sets [personId]'s fraction to [fraction] and rescales everyone else proportionally so the
     * split still sums to 1. [typedText] keeps the raw input while the user is typing.
     */
    fun withShare(personId: String, fraction: Double, typedText: String? = null): ExpenseDraft {
        val f = fraction.coerceIn(0.0, 1.0)
        val current = fractions()
        val others = participants.filter { it != personId }
        val rest = others.sumOf { current[it] ?: 0.0 }
        val remain = 1.0 - f
        val next = buildMap {
            put(personId, f)
            others.forEach {
                val share = if (rest > 1e-9) (current[it] ?: 0.0) / rest * remain else null
                put(it, share ?: (remain / others.size))
            }
        }
        return copy(
            custom = next,
            typed = if (typedText == null) typed - personId else typed + (personId to typedText),
            covered = if (f > 0) covered - personId else covered
        )
    }

    /** "Cover": move [personId]'s whole share onto me. */
    fun coverFor(personId: String): ExpenseDraft {
        val current = fractions().toMutableMap()
        current[ME] = (current[ME] ?: 0.0) + (current[personId] ?: 0.0)
        current[personId] = 0.0
        return copy(custom = current, covered = covered + personId)
    }

    fun withSplit(mode: SplitMode): ExpenseDraft = copy(
        split = mode,
        typed = emptyMap(),
        custom = if (mode == SplitMode.EQUAL) null else (custom ?: equalFractions())
    )

    fun resetSplit(): ExpenseDraft =
        copy(custom = equalFractions(), typed = emptyMap(), covered = emptySet())

    fun toggleParticipant(personId: String): ExpenseDraft = copy(
        participants = if (personId in participants) {
            participants - personId
        } else {
            participants + personId
        },
        custom = null,
        typed = emptyMap(),
        covered = emptySet()
    )

    fun toggleAll(members: List<String>): ExpenseDraft = copy(
        participants = if (participants.size == members.size) listOf(ME) else members,
        custom = null,
        typed = emptyMap(),
        covered = emptySet()
    )
}
