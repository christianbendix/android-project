package com.example.splendit.domain

import com.example.splendit.data.SeedData
import com.example.splendit.data.model.Currency
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private fun seed(id: String) = SeedData.groups.first { it.id == id }

    private fun expense(shares: Map<String, Double>? = null) = Message.Expense(
        id = 1,
        from = ME,
        title = "Dinner",
        total = 400.0,
        payer = ME,
        method = PayMethod.EVEN,
        participants = listOf(ME, "mads", "sofie", "emil"),
        shares = shares,
        timestamp = 0
    )

    @Test
    fun equalShareSplitsTotalByParticipants() {
        assertEquals(100.0, Ledger.share(expense(), "mads"), 1e-9)
    }

    @Test
    fun customShareUsesFraction() {
        val e = expense(mapOf(ME to 0.5, "mads" to 0.25, "sofie" to 0.25, "emil" to 0.0))
        assertEquals(100.0, Ledger.share(e, "mads"), 1e-9)
        assertEquals(0.0, Ledger.share(e, "emil"), 1e-9)
    }

    @Test
    fun balanceIsNegativeWhenIOweTheGroup() {
        // Roskilde: Mads paid 840 for four people, I have not paid my 210.
        assertEquals(-210.0, Ledger.balance(seed("g1")), 1e-9)
    }

    @Test
    fun balanceIsPositiveWhenOthersOweMe() {
        // Kitchen K4: I paid 320 for four, only Oliver has paid me back.
        assertEquals(160.0, Ledger.balance(seed("g2")), 1e-9)
    }

    @Test
    fun balanceIsZeroWhenSettled() {
        assertEquals(0.0, Ledger.balance(seed("g4")), 1e-9)
    }

    @Test
    fun payMarksShareAndFeedsJackpot() {
        val g = seed("g1")
        val result = Ledger.pay(g, messageId = 4, personId = ME)!!
        val bill = result.group.messages.filterIsInstance<Message.Expense>().single()
        assertTrue(ME in bill.paid)
        assertEquals(210.0, result.share, 1e-9)
        assertEquals(21.0, result.cashback, 1e-9)
        assertEquals(482.0 + 21.0, result.group.jackpot!!.pool, 1e-9)
        // Second to pay (Sofie was first): +0.15 rating.
        assertEquals(3.65, result.group.jackpot!!.ratingOf(ME), 1e-9)
        assertEquals(0.0, Ledger.balance(result.group), 1e-9)
    }

    @Test
    fun payingTwiceDoesNothing() {
        val once = Ledger.pay(seed("g1"), 4, ME)!!.group
        assertNull(Ledger.pay(once, 4, ME))
    }

    @Test
    fun ratingIsClampedToFive() {
        // Sofie already has 5.0; in a fresh bill she would pay first (+0.4) but stays at 5.
        val g = seed("g1")
        val fresh = g.copy(
            messages = g.messages + expense().copy(id = 99, payer = "mads", paid = emptySet())
        )
        val result = Ledger.pay(fresh, 99, "sofie")!!
        assertTrue(result.first)
        assertEquals(5.0, result.group.jackpot!!.ratingOf("sofie"), 1e-9)
    }

    @Test
    fun formatsDanishKroner() {
        assertEquals("1.234 kr", formatMoney(1234.4, Currency.DKK))
        assertEquals("210 kr", formatMoney(-210.0, Currency.DKK))
        assertEquals("€50", formatMoney(50.0, Currency.EUR))
    }

    @Test
    fun initialsFromGroupName() {
        assertEquals("RS", Ledger.initials("roskilde summer 2027"))
        assertEquals("K", Ledger.initials("  Kitchen "))
    }
}
