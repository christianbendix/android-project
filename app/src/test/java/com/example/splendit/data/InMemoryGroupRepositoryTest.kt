package com.example.splendit.data

import com.example.splendit.data.SeedData.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.domain.Ledger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryGroupRepositoryTest {
    @Test
    fun createGroupPutsItFirstWithMeAsAdminAndJackpot() = runTest {
        val repo = InMemoryGroupRepository(clock = { 1_000L })
        val id = repo.createGroup("Roskilde 2027", "Festival week", null, listOf("mads"), 500.0)

        val g = repo.groups.value.first()
        assertEquals(id, g.id)
        assertEquals(listOf(ME, "mads"), g.members)
        assertEquals(ME, g.createdBy)
        assertEquals("Festival week", g.description)
        assertEquals("R2", g.initials)
        assertNotNull(g.jackpot)
        assertEquals(0.0, g.jackpot!!.pool, 0.0)
        val texts = g.messages.filterIsInstance<Message.System>().map { it.text }
        assertEquals("You created the group · Jackpot pool on, 500 kr pot", texts[0])
    }

    @Test
    fun createGroupWithoutJackpot() = runTest {
        val repo = InMemoryGroupRepository()
        val id = repo.createGroup("Padel", "", null, listOf("oliver"), null)
        assertNull(repo.group(id)!!.jackpot)
    }

    @Test
    fun payingPostsSystemMessageWithCashback() = runTest {
        val repo = InMemoryGroupRepository()
        repo.pay("g1", "4", ME)
        val last = repo.group("g1")!!.messages.last() as Message.System
        assertEquals("You sent 210 kr to Mads · 21 kr to the jackpot", last.text)
        assertEquals(0.0, Ledger.balance(repo.group("g1")!!, ME), 1e-9)
    }

    @Test
    fun postedBillStaysUnpaidUntilMembersPay() = runTest {
        val repo = InMemoryGroupRepository()
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
        assertEquals(ME, bill.from)
    }

    @Test
    fun findUserMatchesPhoneWithCountryCode() = runTest {
        val repo = InMemoryGroupRepository()
        val found = repo.findUser("+4522410819") as UserLookup.Found
        assertEquals("mads", found.person.id)
        assertEquals(UserLookup.NotFound, repo.findUser("+4599999999"))
    }

    @Test
    fun coveredParticipantsStartAsPaid() = runTest {
        val repo = InMemoryGroupRepository()
        val shares = mapOf(ME to 0.5, "oliver" to 0.25, "ida" to 0.25, "lucas" to 0.0)
        repo.postExpense("g2", "Taxi", 200.0, ME, PayMethod.EVEN, shares.keys.toList(), shares)
        val bill = repo.group("g2")!!.messages.filterIsInstance<Message.Expense>().last()
        assertEquals(setOf("lucas"), bill.paid)
    }

    @Test
    fun jackpotTopUpFillAndPayout() = runTest {
        val repo = InMemoryGroupRepository()
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
