package com.example.splendit.data

import com.example.splendit.auth.Session
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.remote.ExpenseRecord
import com.example.splendit.data.remote.GroupRecord
import com.example.splendit.data.remote.MessageRecord
import com.example.splendit.data.remote.PaymentRecord
import com.example.splendit.data.remote.UserRecord
import com.example.splendit.domain.Ledger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteGroupRepositoryTest {
    private val peter = UserRecord("u1", "Peter Hansen", "+4511111111")
    private val sofie = UserRecord("u2", "Sofie Lund", "+4520000002")
    private val mads = UserRecord("u3", "Mads Holm", "+4520000003")
    private val remote = FakeGroupRemote(listOf(peter, sofie, mads))
    private val session = MutableStateFlow<Session?>(null)
    private val events = mutableListOf<GroupEvent>()

    private val trip = GroupRecord(
        id = "g1",
        name = "Roskilde 2026",
        description = "Tent and food",
        admin = "u2",
        members = listOf("u1", "u2", "u3"),
        created = 1_000
    )

    private fun expense(id: String, by: String, payer: String = by, created: Long = 2_000) =
        ExpenseRecord(
            id = id,
            group = "g1",
            creator = by,
            title = "Groceries",
            amountOre = 45_000,
            payer = payer,
            method = "even",
            date = created,
            participants = listOf("u1", "u2", "u3"),
            shares = null,
            created = created
        )

    private fun TestScope.signedIn(user: UserRecord = peter): RemoteGroupRepository {
        val repo = RemoteGroupRepository(
            remote,
            session,
            backgroundScope,
            clock = { 10_000 + testScheduler.currentTime },
            retry = RemoteGroupRepository.RetryPolicy(minMillis = 1_000, maxMillis = 4_000)
        )
        backgroundScope.launch { repo.events.toList(events) }
        session.value = Session("tok", user.id, "${user.id}@example.com", user.name)
        runCurrent()
        return repo
    }

    private fun RemoteGroupRepository.g1() = group("g1")!!

    @Test
    fun loadsGroupsOnLogin_andWorksOutWhatTheSignedInUserOwes() = runTest {
        remote.storeQuietly(trip)
        remote.storeQuietly(expense("e1", by = "u2"))

        val repo = signedIn()

        assertEquals("u1", repo.me)
        val g = repo.g1()
        assertEquals("u2", g.createdBy)
        assertEquals("Tent and food", g.description)
        assertEquals(-150.0, Ledger.balance(g, "u1"), 1e-9)
        assertEquals(300.0, Ledger.balance(g, "u2"), 1e-9)
        assertEquals("Sofie", repo.person("u2").shortName)
        val system = g.messages.filterIsInstance<Message.System>().map { it.text }
        assertEquals("Sofie created the group", system.first())
        assertTrue("nothing loaded at login is announced", events.isEmpty())
    }

    @Test
    fun anotherMembersExpense_isAppliedLiveAndNotified() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()

        remote.push(expense("e1", by = "u2"))
        runCurrent()

        assertEquals(-150.0, Ledger.balance(repo.g1(), "u1"), 1e-9)
        assertEquals(1, repo.g1().unread)
        assertEquals(
            listOf(GroupEvent.NewExpense("g1", "Roskilde 2026", "Sofie", "Groceries", "150 kr")),
            events
        )
        repo.markRead("g1")
        assertEquals(0, repo.g1().unread)
    }

    @Test
    fun beingAddedToAGroup_isNotified() = runTest {
        val repo = signedIn()

        remote.push(trip.copy(admin = "u3"))
        runCurrent()

        assertEquals(listOf("g1"), repo.groups.value.map { it.id })
        assertEquals(listOf(GroupEvent.AddedToGroup("g1", "Roskilde 2026", "Mads")), events)
    }

    @Test
    fun myOwnWrites_areNotNotifiedWhenTheServerEchoesThem() = runTest {
        val repo = signedIn()
        val id = repo.createGroup("Padel", "", null, listOf("u2"), jackpotGoal = null)
        repo.postExpense("g1", "x", 1.0, "u1", PayMethod.EVEN, listOf("u1", "u2"), null)
        runCurrent()

        assertTrue(id in remote.records)
        assertTrue(events.isEmpty())
        assertEquals(0, repo.group(id)!!.unread)
    }

    @Test
    fun payingMyShare_writesAPaymentAndSettlesAtOnce() = runTest {
        remote.storeQuietly(trip)
        remote.storeQuietly(expense("e1", by = "u2"))
        val repo = signedIn()

        repo.pay("g1", "e1", "u1")

        // Shown before the server confirms...
        assertEquals(0.0, Ledger.balance(repo.g1(), "u1"), 1e-9)
        runCurrent()
        // ...and sent as a payment record from me to the payer.
        val payment = remote.records.values.filterIsInstance<PaymentRecord>().single()
        assertEquals("u1", payment.from)
        assertEquals("u2", payment.to)
        assertEquals(15_000L, payment.amountOre)
        assertEquals("You paid Sofie 150 kr", (repo.g1().messages.last() as Message.System).text)

        repo.pay("g1", "e1", "u1")
        runCurrent()
        assertEquals(
            "paying twice is a no-op",
            1,
            remote.records.values.count {
                it is PaymentRecord
            }
        )
    }

    @Test
    fun cannotRecordSomeoneElsesPayment() = runTest {
        remote.storeQuietly(trip)
        remote.storeQuietly(expense("e1", by = "u2"))
        val repo = signedIn()

        repo.pay("g1", "e1", "u3")
        runCurrent()

        assertTrue(remote.records.values.none { it is PaymentRecord })
    }

    @Test
    fun theMemberIPaidFor_payingMeIsNotified() = runTest {
        remote.storeQuietly(trip)
        remote.storeQuietly(expense("e1", by = "u1"))
        val repo = signedIn()
        assertEquals(300.0, Ledger.balance(repo.g1(), "u1"), 1e-9)

        remote.push(PaymentRecord("p1", "g1", "e1", "u2", "u1", 15_000, created = 3_000))
        runCurrent()

        assertEquals(150.0, Ledger.balance(repo.g1(), "u1"), 1e-9)
        assertEquals("Sofie paid you 150 kr", (repo.g1().messages.last() as Message.System).text)
        assertEquals(
            listOf(GroupEvent.PaymentReceived("g1", "Roskilde 2026", "Sofie", "150 kr")),
            events
        )
    }

    @Test
    fun customSharesAreStoredInOreAndAddUpToTheTotal() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()

        repo.postExpense(
            "g1",
            "Taxi",
            100.0,
            "u1",
            PayMethod.EVEN,
            listOf("u1", "u2", "u3"),
            mapOf("u1" to 1.0 / 3, "u2" to 1.0 / 3, "u3" to 1.0 / 3)
        )
        runCurrent()

        val sent = remote.records.values.filterIsInstance<ExpenseRecord>().single()
        assertEquals(10_000L, sent.amountOre)
        assertEquals(10_000L, sent.shares!!.values.sum())
        assertEquals(3_334L, sent.shares!!.getValue("u1"))
    }

    @Test
    fun failedWrite_showsAtOnce_retriesInTheBackground_andIsStoredOnce() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()
        // The first two attempts time out, but the first one did reach the server.
        remote.failNextCreates = 2
        remote.lostResponse = true

        repo.sendText("g1", "On my way")

        assertEquals("On my way", (repo.g1().messages.last() as Message.Text).text)
        runCurrent()
        assertEquals(1, remote.createCalls)
        advanceTimeBy(1_001)
        assertEquals(2, remote.createCalls)
        advanceTimeBy(2_001)
        assertEquals(3, remote.createCalls)
        advanceTimeBy(10_000)
        assertEquals("no more attempts once it succeeded", 3, remote.createCalls)
        assertEquals(1, remote.records.values.count { it is MessageRecord })
        assertEquals(1, repo.g1().messages.count { it is Message.Text })
    }

    @Test
    fun writesMadeOffline_areSentInOrderOnceBackOnline() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()
        remote.offline = true

        repo.postExpense("g1", "Pizza", 300.0, "u1", PayMethod.EVEN, listOf("u1", "u2"), null)
        repo.sendText("g1", "Pizza is paid")
        advanceTimeBy(20_000)

        // Still usable offline: both show, neither has reached the server.
        assertEquals(2, repo.g1().messages.count { it is Message.Expense || it is Message.Text })
        assertTrue(remote.records.values.none { it is ExpenseRecord || it is MessageRecord })

        remote.offline = false
        advanceTimeBy(4_001)

        val sent = remote.records.values.filter { it is ExpenseRecord || it is MessageRecord }
        assertEquals(listOf("expenses", "messages"), sent.map { it.collection })
    }

    @Test
    fun rejectedWrite_isRolledBack() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()
        remote.rejectWith = 403

        repo.sendText("g1", "Hi")
        assertTrue(repo.g1().messages.any { it is Message.Text })
        runCurrent()

        assertTrue(repo.g1().messages.none { it is Message.Text })
        assertEquals("a refusal is not retried", 1, remote.createCalls)
    }

    @Test
    fun changesMissedWhileDisconnected_areLoadedAndNotifiedOnReconnect() = runTest {
        remote.storeQuietly(trip)
        val repo = signedIn()

        remote.disconnect()
        runCurrent()
        remote.storeQuietly(expense("e1", by = "u3"))
        advanceTimeBy(1_001)

        assertEquals(-150.0, Ledger.balance(repo.g1(), "u1"), 1e-9)
        assertEquals("Mads", (events.single() as GroupEvent.NewExpense).by)
    }

    @Test
    fun offlineAtLogin_keepsRetryingUntilTheServerAnswers() = runTest {
        remote.storeQuietly(trip)
        remote.offline = true
        val repo = signedIn()
        assertTrue(repo.groups.value.isEmpty())

        remote.offline = false
        advanceTimeBy(1_001)

        assertEquals(listOf("g1"), repo.groups.value.map { it.id })
    }

    @Test
    fun logout_dropsTheGroups_soTheNextAccountStartsEmpty() = runTest {
        remote.storeQuietly(trip)
        remote.storeQuietly(trip.copy(id = "g2", name = "Sofie only", members = listOf("u2")))
        val repo = signedIn()
        assertTrue(repo.groups.value.isNotEmpty())

        session.value = null
        runCurrent()

        assertTrue(repo.groups.value.isEmpty())
        assertEquals("", repo.me)
        assertNull(repo.group("g1"))

        // The fake shows every record; the real server filters by member.
        session.value = Session("tok2", "u2", "u2@example.com", "Sofie Lund")
        runCurrent()
        assertEquals("u2", repo.me)
        assertEquals(2, repo.groups.value.size)
    }

    @Test
    fun findUser_makesTheirNameKnown() = runTest {
        val repo = signedIn()

        val found = repo.findUser("+4520000003") as UserLookup.Found

        assertEquals("Mads Holm", found.person.fullName)
        assertEquals("Mads", repo.person("u3").shortName)
        assertEquals(UserLookup.NotFound, repo.findUser("+4599999999"))
        remote.offline = true
        assertEquals(UserLookup.Failed, repo.findUser("+4520000003"))
    }
}
