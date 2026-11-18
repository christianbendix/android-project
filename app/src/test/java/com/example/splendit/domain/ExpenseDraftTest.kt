package com.example.splendit.domain

import com.example.splendit.data.model.ME
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseDraftTest {
    private val members = listOf(ME, "mads", "sofie", "emil")
    private val draft = ExpenseDraft(
        title = "Groceries",
        amount = "800",
        participants = members,
        method = PayMethod.WHEEL
    )

    @Test
    fun equalSplitIsValid() {
        assertTrue(draft.isValid)
        assertEquals(0.25, draft.fractions().getValue("mads"), 1e-9)
    }

    @Test
    fun commaDecimalIsParsed() {
        assertEquals(12.5, draft.copy(amount = "12,5").amountValue, 1e-9)
    }

    @Test
    fun invalidWithoutTitleAmountOrEnoughPeople() {
        assertFalse(draft.copy(title = " ").isValid)
        assertFalse(draft.copy(amount = "").isValid)
        assertFalse(draft.copy(participants = listOf(ME)).isValid)
    }

    @Test
    fun settingOneShareRescalesTheOthers() {
        val d = draft.withSplit(SplitMode.PERCENT).withShare("mads", 0.4)
        val f = d.fractions()
        assertEquals(0.4, f.getValue("mads"), 1e-9)
        assertEquals(0.2, f.getValue(ME), 1e-9)
        assertEquals(1.0, d.assigned, 1e-9)
        assertTrue(d.isValid)
    }

    @Test
    fun coverMovesShareOntoMe() {
        val d = draft.withSplit(SplitMode.AMOUNT).coverFor("emil")
        val f = d.fractions()
        assertEquals(0.0, f.getValue("emil"), 1e-9)
        assertEquals(0.5, f.getValue(ME), 1e-9)
        assertTrue("emil" in d.covered)
        assertEquals(1.0, d.assigned, 1e-9)
    }

    @Test
    fun givingACoveredPersonAShareUncoversThem() {
        val d = draft.withSplit(SplitMode.AMOUNT).coverFor("emil").withShare("emil", 0.1)
        assertFalse("emil" in d.covered)
    }

    @Test
    fun changingParticipantsResetsCustomSplit() {
        val d = draft.withSplit(SplitMode.PERCENT).withShare("mads", 0.7).toggleParticipant("emil")
        assertEquals(3, d.participants.size)
        assertEquals(1.0 / 3, d.fractions().getValue("mads"), 1e-9)
    }

    @Test
    fun toggleAllClearsToJustMeThenSelectsEveryone() {
        val cleared = draft.toggleAll(members)
        assertEquals(listOf(ME), cleared.participants)
        assertEquals(members, cleared.toggleAll(members).participants)
    }

    @Test
    fun backToEqualDropsCustomFractions() {
        val d = draft.withSplit(SplitMode.PERCENT).withShare("mads", 0.7).withSplit(SplitMode.EQUAL)
        assertEquals(0.25, d.fractions().getValue("mads"), 1e-9)
    }
}
