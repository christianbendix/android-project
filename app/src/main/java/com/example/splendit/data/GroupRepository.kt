package com.example.splendit.data

import com.example.splendit.data.model.AppSettings
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Jackpot
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person
import com.example.splendit.domain.Ledger
import com.example.splendit.domain.formatMoney
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Model in MVVM: the only place group data is changed. ViewModels observe [groups] and
 * call the functions below; they never mutate a Group themselves.
 */
interface GroupRepository {
    val groups: StateFlow<List<Group>>
    val settings: StateFlow<AppSettings>

    fun person(id: String): Person
    fun group(id: String): Group?
    fun format(amount: Double): String

    fun markRead(groupId: String)
    fun createGroup(
        name: String,
        photoUri: String?,
        memberIds: List<String>,
        invites: List<Person>,
        jackpotGoal: Double?
    ): String

    fun sendText(groupId: String, text: String)
    fun sendImage(groupId: String, uri: String)
    fun postExpense(
        groupId: String,
        title: String,
        total: Double,
        payer: String,
        method: PayMethod,
        participants: List<String>,
        shares: Map<String, Double>?
    )

    fun pay(groupId: String, messageId: Long, personId: String)
    fun topUpJackpot(groupId: String, amount: Double)
    fun fillJackpot(groupId: String)
    fun payOutJackpot(groupId: String, winner: String, prize: Double)
}

/**
 * In-memory implementation seeded with the prototype's demo groups. [scope] runs the simulated
 * friends who pay their share a few seconds after a bill is posted.
 */
class InMemoryGroupRepository(
    private val scope: CoroutineScope,
    initial: List<Group> = SeedData.groups,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis
) : GroupRepository {
    private val _groups = MutableStateFlow(initial)
    override val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val invited = MutableStateFlow<Map<String, Person>>(emptyMap())
    private val ids = AtomicLong(clock())

    override fun person(id: String): Person = SeedData.people[id]
        ?: invited.value[id]
        ?: Person(id, id, id, "?", "")

    override fun group(id: String): Group? = _groups.value.firstOrNull { it.id == id }

    override fun format(amount: Double): String = formatMoney(amount, _settings.value.currency)

    private fun update(groupId: String, transform: (Group) -> Group) {
        _groups.update { list -> list.map { if (it.id == groupId) transform(it) else it } }
    }

    private fun Group.touched(): Group = copy(updated = clock(), whenLabel = "Now")

    private fun Group.plus(message: Message): Group = copy(messages = messages + message).touched()

    override fun markRead(groupId: String) = update(groupId) { it.copy(unread = 0) }

    override fun createGroup(
        name: String,
        photoUri: String?,
        memberIds: List<String>,
        invites: List<Person>,
        jackpotGoal: Double?
    ): String {
        invited.update { current -> current + invites.associateBy { it.id } }
        val id = "g${ids.incrementAndGet()}"
        val members = listOf(ME) + memberIds
        val created = buildString {
            append("You created the group")
            if (jackpotGoal != null) append(" · Jackpot pool on, ${format(jackpotGoal)} pot")
        }
        val inviteCount = memberIds.count { it in invited.value }
        val messages = buildList {
            add(Message.System(ids.incrementAndGet(), created))
            if (inviteCount > 0) {
                val plural = if (inviteCount > 1) "s" else ""
                add(Message.System(ids.incrementAndGet(), "$inviteCount SMS invite$plural sent"))
            }
        }
        val group = Group(
            id = id,
            name = name.trim(),
            initials = Ledger.initials(name),
            photoUri = photoUri,
            members = members,
            createdBy = ME,
            updated = clock(),
            whenLabel = "Now",
            jackpot = jackpotGoal?.let { goal ->
                Jackpot(goal = goal, pool = 0.0, ratings = members.associateWith { 3.0 })
            },
            messages = messages
        )
        _groups.update { listOf(group) + it }
        return id
    }

    override fun sendText(groupId: String, text: String) =
        update(groupId) { it.plus(Message.Text(ids.incrementAndGet(), ME, text)) }

    override fun sendImage(groupId: String, uri: String) =
        update(groupId) { it.plus(Message.Image(ids.incrementAndGet(), ME, uri)) }

    override fun postExpense(
        groupId: String,
        title: String,
        total: Double,
        payer: String,
        method: PayMethod,
        participants: List<String>,
        shares: Map<String, Double>?
    ) {
        val id = ids.incrementAndGet()
        // People whose share is zero (covered by someone else) have nothing to pay.
        val alreadySettled = participants
            .filter { it != payer && (shares?.get(it) ?: 1.0) < 1e-6 }
            .toSet()
        val expense = Message.Expense(
            id = id,
            from = ME,
            title = title.trim(),
            total = total,
            payer = payer,
            method = method,
            participants = participants,
            shares = shares,
            paid = alreadySettled,
            timestamp = clock()
        )
        update(groupId) { it.plus(expense) }

        // Simulate the other members paying their share after a few seconds.
        participants
            .filter { it != payer && it != ME && it !in alreadySettled && it in SeedData.people }
            .forEachIndexed { i, personId ->
                scope.launch {
                    delay(2600L + i * 2400L + random.nextLong(1200))
                    pay(groupId, id, personId)
                }
            }
    }

    override fun pay(groupId: String, messageId: Long, personId: String) = update(groupId) { g ->
        val expense = g.messages.filterIsInstance<Message.Expense>().firstOrNull {
            it.id ==
                messageId
        }
        val result = Ledger.pay(g, messageId, personId)
        if (expense == null || result == null) return@update g
        val who = if (personId == ME) "You" else person(personId).shortName
        val to = if (expense.payer == ME) "you" else person(expense.payer).shortName
        val note = if (g.jackpot != null) {
            " · ${format(result.cashback)} to the jackpot" +
                if (result.first) " · first to pay ★" else ""
        } else {
            ""
        }
        result.group.plus(
            Message.System(ids.incrementAndGet(), "$who sent ${format(result.share)} to $to$note")
        )
    }

    override fun topUpJackpot(groupId: String, amount: Double) = update(groupId) { g ->
        val jackpot = g.jackpot ?: return@update g
        g.copy(jackpot = jackpot.copy(pool = jackpot.pool + amount)).plus(
            Message.System(ids.incrementAndGet(), "You added ${format(amount)} to the jackpot")
        )
    }

    override fun fillJackpot(groupId: String) = update(groupId) { g ->
        val jackpot = g.jackpot ?: return@update g
        g.copy(jackpot = jackpot.copy(pool = jackpot.goal))
    }

    override fun payOutJackpot(groupId: String, winner: String, prize: Double) =
        update(groupId) { g ->
            val jackpot = g.jackpot ?: return@update g
            val name = if (winner == ME) "You" else person(winner).shortName
            g.copy(jackpot = jackpot.copy(pool = 0.0)).plus(
                Message.System(
                    ids.incrementAndGet(),
                    "★ $name won the ${format(prize)} jackpot. A new pot starts now."
                )
            )
        }
}
