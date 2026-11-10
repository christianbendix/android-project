package com.example.splendit.data

import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person
import com.example.splendit.domain.Ledger
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryGroupRepositoryTest {
    @Test
    fun createGroupPutsItFirstWithJackpotAndInviteMessages() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope, clock = { 1_000L })
        val invite = Person("tel12345678", "+45 12 34 56 78", "+45 12 34 56 78", "#", "")
        val id = repo.createGroup(
            "Roskilde 2027",
            null,
            listOf("mads", invite.id),
            listOf(invite),
            500.0
        )

        val g = repo.groups.value.first()
        assertEquals(id, g.id)
        assertEquals(listOf(ME, "mads", invite.id), g.members)
        assertEquals("R2", g.initials)
        assertNotNull(g.jackpot)
        assertEquals(0.0, g.jackpot!!.pool, 0.0)
        val texts = g.messages.filterIsInstance<Message.System>().map { it.text }
        assertEquals("You created the group · Jackpot pool on, 500 kr pot", texts[0])
        assertEquals("1 SMS invite sent", texts[1])
        assertEquals("+45 12 34 56 78", repo.person(invite.id).fullName)
    }

    @Test
    fun createGroupWithoutJackpot() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val id = repo.createGroup("Padel", null, listOf("oliver"), emptyList(), null)
        assertNull(repo.group(id)!!.jackpot)
    }

    @Test
    fun payingPostsSystemMessageWithCashback() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        repo.pay("g1", 4, ME)
        val last = repo.group("g1")!!.messages.last() as Message.System
        assertEquals("You sent 210 kr to Mads · 21 kr to the jackpot", last.text)
        assertEquals(0.0, Ledger.balance(repo.group("g1")!!), 1e-9)
    }

    @Test
    fun friendsPayTheirShareInTheBackgroundAfterPosting() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope, random = Random(1))
        repo.postExpense(
            "g2",
            "Pizza",
            400.0,
            ME,
            PayMethod.EVEN,
            listOf(ME, "oliver", "ida", "lucas"),
            null
        )
        val bill = repo.group("g2")!!.messages.filterIsInstance<Message.Expense>().last()
        assertTrue(bill.paid.isEmpty())

        // Nobody pays instantly...
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(
            repo.group("g2")!!.messages.filterIsInstance<Message.Expense>().last().paid.isEmpty()
        )

        // ...but eventually all three have paid.
        advanceUntilIdle()
        val paid = repo.group("g2")!!.messages.filterIsInstance<Message.Expense>().last().paid
        assertEquals(setOf("oliver", "ida", "lucas"), paid)
    }

    @Test
    fun coveredParticipantsStartAsPaid() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val shares = mapOf(ME to 0.5, "oliver" to 0.25, "ida" to 0.25, "lucas" to 0.0)
        repo.postExpense("g2", "Taxi", 200.0, ME, PayMethod.EVEN, shares.keys.toList(), shares)
        val bill = repo.group("g2")!!.messages.filterIsInstance<Message.Expense>().last()
        assertEquals(setOf("lucas"), bill.paid)
    }

    @Test
    fun jackpotTopUpFillAndPayout() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        repo.topUpJackpot("g3", 50.0)
        assertEquals(390.0, repo.group("g3")!!.jackpot!!.pool, 1e-9)
        repo.fillJackpot("g3")
        assertTrue(repo.group("g3")!!.jackpot!!.isFull)
        repo.payOutJackpot("g3", "clara", 1000.0)
        assertEquals(0.0, repo.group("g3")!!.jackpot!!.pool, 1e-9)
        val last = repo.group("g3")!!.messages.last() as Message.System
        assertEquals("★ Clara won the 1.000 kr jackpot. A new pot starts now.", last.text)
    }
}
