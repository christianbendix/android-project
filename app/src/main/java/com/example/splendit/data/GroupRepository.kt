package com.example.splendit.data

import com.example.splendit.data.model.AppSettings
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Jackpot
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person
import com.example.splendit.domain.Ledger
import com.example.splendit.domain.formatMoney
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update

/**
 * The Model in MVVM: the only place group data is changed. ViewModels observe [groups] and
 * call the functions below; they never mutate a Group themselves.
 */
interface GroupRepository {
    val groups: StateFlow<List<Group>>
    val settings: StateFlow<AppSettings>

    /** Id of the signed-in user, as stored in the groups' member lists. */
    val me: String

    /** Changes made by other members, for notifications. Never includes my own writes. */
    val events: Flow<GroupEvent> get() = emptyFlow()

    /** False when photos in the chat can't be shared with the other members yet. */
    val supportsPhotos: Boolean get() = true

    fun person(id: String): Person
    fun group(id: String): Group?
    fun format(amount: Double): String

    /** Everyone I share a group with, for the "add members" list. */
    fun knownPeople(): List<Person>

    /** Finds a registered user by phone number (with country code) or email. */
    suspend fun findUser(phoneOrEmail: String): UserLookup

    fun markRead(groupId: String)

    /** Creates the group with me as admin and returns its id straight away. */
    fun createGroup(
        name: String,
        description: String,
        photoUri: String?,
        memberIds: List<String>,
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

    /** Marks [personId]'s share of expense [expenseId] as paid. */
    fun pay(groupId: String, expenseId: String, personId: String)
    fun topUpJackpot(groupId: String, amount: Double)
    fun fillJackpot(groupId: String)
    fun payOutJackpot(groupId: String, winner: String, prize: Double)
}

sealed interface UserLookup {
    data class Found(val person: Person) : UserLookup
    data object NotFound : UserLookup
    data object Failed : UserLookup
}

/**
 * In-memory implementation seeded with the prototype's demo groups. The signed-in app uses
 * [RemoteGroupRepository]; this one backs unit tests, UI tests and previews.
 */
class InMemoryGroupRepository(
    initial: List<Group> = SeedData.groups,
    override val me: String = SeedData.ME,
    private val clock: () -> Long = System::currentTimeMillis
) : GroupRepository {
    private val _groups = MutableStateFlow(initial)
    override val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val ids = AtomicLong(clock())

    private fun nextId(): String = ids.incrementAndGet().toString()

    override fun person(id: String): Person = SeedData.people[id] ?: Person(id, id, id, "?", "")

    override fun group(id: String): Group? = _groups.value.firstOrNull { it.id == id }

    override fun format(amount: Double): String = formatMoney(amount, _settings.value.currency)

    override fun knownPeople(): List<Person> = SeedData.friends.map(::person)

    override suspend fun findUser(phoneOrEmail: String): UserLookup {
        val needle = phoneOrEmail.filter { it.isDigit() || it == '+' }
        val match = SeedData.people.values.firstOrNull {
            it.id != me && it.phone.filter { c -> c.isDigit() || c == '+' } == needle
        }
        return match?.let { UserLookup.Found(it) } ?: UserLookup.NotFound
    }

    private fun update(groupId: String, transform: (Group) -> Group) {
        _groups.update { list -> list.map { if (it.id == groupId) transform(it) else it } }
    }

    private fun Group.touched(): Group = copy(updated = clock(), whenLabel = "Now")

    private fun Group.plus(message: Message): Group = copy(messages = messages + message).touched()

    override fun markRead(groupId: String) = update(groupId) { it.copy(unread = 0) }

    override fun createGroup(
        name: String,
        description: String,
        photoUri: String?,
        memberIds: List<String>,
        jackpotGoal: Double?
    ): String {
        val id = "g${nextId()}"
        val members = listOf(me) + memberIds.filter { it != me }
        val created = buildString {
            append("You created the group")
            if (jackpotGoal != null) append(" · Jackpot pool on, ${format(jackpotGoal)} pot")
        }
        val group = Group(
            id = id,
            name = name.trim(),
            initials = Ledger.initials(name),
            description = description.trim(),
            photoUri = photoUri,
            members = members,
            createdBy = me,
            updated = clock(),
            whenLabel = "Now",
            jackpot = jackpotGoal?.let { goal ->
                Jackpot(goal = goal, pool = 0.0, ratings = members.associateWith { 3.0 })
            },
            messages = listOf(Message.System(nextId(), created))
        )
        _groups.update { listOf(group) + it }
        return id
    }

    override fun sendText(groupId: String, text: String) =
        update(groupId) { it.plus(Message.Text(nextId(), me, text)) }

    override fun sendImage(groupId: String, uri: String) =
        update(groupId) { it.plus(Message.Image(nextId(), me, uri)) }

    override fun postExpense(
        groupId: String,
        title: String,
        total: Double,
        payer: String,
        method: PayMethod,
        participants: List<String>,
        shares: Map<String, Double>?
    ) {
        // People whose share is zero (covered by someone else) have nothing to pay.
        val alreadySettled = participants
            .filter { it != payer && (shares?.get(it) ?: 1.0) < 1e-6 }
            .toSet()
        val expense = Message.Expense(
            id = nextId(),
            from = me,
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
    }

    override fun pay(groupId: String, expenseId: String, personId: String) = update(groupId) { g ->
        val expense = g.messages.filterIsInstance<Message.Expense>()
            .firstOrNull { it.id == expenseId }
        val result = Ledger.pay(g, expenseId, personId)
        if (expense == null || result == null) return@update g
        val who = if (personId == me) "You" else person(personId).shortName
        val to = if (expense.payer == me) "you" else person(expense.payer).shortName
        val note = if (g.jackpot != null) {
            " · ${format(result.cashback)} to the jackpot" +
                if (result.first) " · first to pay ★" else ""
        } else {
            ""
        }
        result.group.plus(Message.System(nextId(), "$who sent ${format(result.share)} to $to$note"))
    }

    override fun topUpJackpot(groupId: String, amount: Double) = update(groupId) { g ->
        val jackpot = g.jackpot ?: return@update g
        g.copy(jackpot = jackpot.copy(pool = jackpot.pool + amount)).plus(
            Message.System(nextId(), "You added ${format(amount)} to the jackpot")
        )
    }

    override fun fillJackpot(groupId: String) = update(groupId) { g ->
        val jackpot = g.jackpot ?: return@update g
        g.copy(jackpot = jackpot.copy(pool = jackpot.goal))
    }

    override fun payOutJackpot(groupId: String, winner: String, prize: Double) =
        update(groupId) { g ->
            val jackpot = g.jackpot ?: return@update g
            val name = if (winner == me) "You" else person(winner).shortName
            g.copy(jackpot = jackpot.copy(pool = 0.0)).plus(
                Message.System(
                    nextId(),
                    "★ $name won the ${format(prize)} jackpot. A new pot starts now."
                )
            )
        }
}
