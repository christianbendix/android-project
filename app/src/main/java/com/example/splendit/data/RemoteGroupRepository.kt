package com.example.splendit.data

import com.example.splendit.auth.Session
import com.example.splendit.data.model.AppSettings
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person
import com.example.splendit.data.remote.ExpenseRecord
import com.example.splendit.data.remote.GroupRecord
import com.example.splendit.data.remote.GroupRemote
import com.example.splendit.data.remote.HttpException
import com.example.splendit.data.remote.MessageRecord
import com.example.splendit.data.remote.PaymentRecord
import com.example.splendit.data.remote.RecordJson
import com.example.splendit.data.remote.RemoteChange
import com.example.splendit.data.remote.RemoteRecord
import com.example.splendit.data.remote.UserRecord
import com.example.splendit.domain.Ledger
import com.example.splendit.domain.formatMoney
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Group data shared through PocketBase. While someone is signed in it loads everything they can
 * see, then applies realtime changes as other members make them. Writes show up immediately and
 * are sent in the background, in order, retrying transient failures until they succeed. Logging
 * out drops the data and closes the connection, so the next account starts empty.
 *
 * [scope] should be the application scope; the work is tied to the session, not to a screen.
 */
class RemoteGroupRepository(
    private val remote: GroupRemote,
    session: Flow<Session?>,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retry: RetryPolicy = RetryPolicy()
) : GroupRepository {

    /** Backoff for reconnecting and for re-sending writes, doubling up to [maxMillis]. */
    data class RetryPolicy(val minMillis: Long = 1_000, val maxMillis: Long = 30_000) {
        fun next(current: Long) = (current * 2).coerceAtMost(maxMillis)
    }

    private data class State(
        val userId: String = "",
        val loaded: Boolean = false,
        val records: Map<String, RemoteRecord> = emptyMap(),
        val users: Map<String, UserRecord> = emptyMap(),
        /** Written here but not yet confirmed by the server; kept across reloads. */
        val pending: Map<String, RemoteRecord> = emptyMap(),
        val unread: Map<String, Int> = emptyMap()
    )

    private val lock = Any()
    private var state = State()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    override val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _events = MutableSharedFlow<GroupEvent>(extraBufferCapacity = 64)
    override val events: Flow<GroupEvent> = _events.asSharedFlow()

    override val supportsPhotos: Boolean = false

    override val me: String get() = synchronized(lock) { state.userId }

    /** Lives as long as the current session; writes run here so logout cancels them. */
    private var sessionScope: CoroutineScope? = null
    private val outbox = Mutex()

    init {
        scope.launch {
            session.map { it?.userId }.distinctUntilChanged().collectLatest { userId ->
                mutate { State(userId = userId.orEmpty()) }
                if (userId != null) {
                    coroutineScope {
                        sessionScope = this
                        try {
                            sync()
                        } finally {
                            sessionScope = null
                        }
                    }
                }
            }
        }
    }

    // Sync

    private suspend fun sync() {
        // Load straight away so the app works even if realtime can't connect.
        tryReload()
        var backoff = retry.minMillis
        while (true) {
            try {
                remote.changes().collect { change ->
                    when (change) {
                        RemoteChange.Connected -> {
                            reload()
                            backoff = retry.minMillis
                        }

                        is RemoteChange.Upsert -> receive(change.record)

                        is RemoteChange.Deleted -> mutate { s ->
                            s.copy(records = s.records - key(change.collection, change.id))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dropped connection or a bad record: fall back to polling until reconnected.
            }
            delay(backoff)
            backoff = retry.next(backoff)
            tryReload()
        }
    }

    private suspend fun tryReload() {
        try {
            reload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: keep showing what we have.
        }
    }

    private suspend fun reload() {
        val snapshot = remote.fetchAll()
        val (before, wasLoaded) = synchronized(lock) { state.records to state.loaded }
        mutate { s ->
            val fresh = snapshot.records.associateBy(::key)
            s.copy(
                loaded = true,
                records = fresh + s.pending.filterKeys { it !in fresh },
                users = s.users + snapshot.users.associateBy { it.id }
            )
        }
        // Changes made while we were disconnected still deserve a notification.
        if (wasLoaded) snapshot.records.filter { key(it) !in before }.forEach(::announce)
    }

    private suspend fun receive(record: RemoteRecord) {
        val missing = peopleIn(record) - synchronized(lock) { state.users.keys }
        if (missing.isNotEmpty()) {
            try {
                val found = remote.fetchUsers(missing)
                mutate { s -> s.copy(users = s.users + found.associateBy { it.id }) }
            } catch (e: IOException) {
                // Names show as "Someone" until the next reload.
            }
        }
        var isNew = false
        mutate { s ->
            isNew = key(record) !in s.records
            s.copy(records = s.records + (key(record) to record))
        }
        if (isNew) announce(record)
    }

    private fun peopleIn(record: RemoteRecord): Set<String> = when (record) {
        is GroupRecord -> record.members.toSet() + record.admin
        is ExpenseRecord -> record.participants.toSet() + record.payer + record.creator
        is PaymentRecord -> setOf(record.from, record.to)
        is MessageRecord -> setOf(record.from)
    }

    /** Counts unread and raises a [GroupEvent] for a record someone else created. */
    private fun announce(record: RemoteRecord) {
        val me = me
        val author = when (record) {
            is GroupRecord -> record.admin
            is ExpenseRecord -> record.creator
            is PaymentRecord -> record.from
            is MessageRecord -> record.from
        }
        if (author == me) return
        val groupId = if (record is GroupRecord) record.id else groupOf(record)
        mutate { s -> s.copy(unread = s.unread + (groupId to (s.unread[groupId] ?: 0) + 1)) }
        val groupName = group(groupId)?.name ?: return
        val event = when (record) {
            is GroupRecord -> GroupEvent.AddedToGroup(groupId, groupName, person(author).shortName)

            is ExpenseRecord -> if (me in record.participants && record.payer != me) {
                val share = record.shares?.get(me)?.let { it / 100.0 }
                    ?: (record.amountOre / 100.0 / record.participants.size)
                GroupEvent.NewExpense(
                    groupId,
                    groupName,
                    person(author).shortName,
                    record.title,
                    format(share)
                )
            } else {
                null
            }

            is PaymentRecord -> if (record.to == me) {
                GroupEvent.PaymentReceived(
                    groupId,
                    groupName,
                    person(author).shortName,
                    format(record.amountOre / 100.0)
                )
            } else {
                null
            }

            is MessageRecord -> null
        }
        event?.let(_events::tryEmit)
    }

    private fun groupOf(record: RemoteRecord): String = when (record) {
        is GroupRecord -> record.id
        is ExpenseRecord -> record.group
        is PaymentRecord -> record.group
        is MessageRecord -> record.group
    }

    // Writes

    /** Shows [record] at once, then sends it in the background until the server has it. */
    private fun write(record: RemoteRecord) {
        val scope = sessionScope ?: return
        val k = key(record)
        mutate { s ->
            s.copy(
                records = s.records + (k to record),
                pending =
                s.pending + (k to record)
            )
        }
        scope.launch {
            // One write at a time, in the order they were made: a payment needs its expense.
            outbox.withLock {
                var backoff = retry.minMillis
                while (true) {
                    try {
                        remote.create(record)
                        mutate { s -> s.copy(pending = s.pending - k) }
                        return@withLock
                    } catch (e: HttpException) {
                        if (!e.isTransient) {
                            // Rejected (e.g. by an access rule); retrying won't help.
                            mutate { s -> s.copy(records = s.records - k, pending = s.pending - k) }
                            return@withLock
                        }
                    } catch (e: IOException) {
                        // Offline or timed out: try again shortly.
                    }
                    delay(backoff)
                    backoff = retry.next(backoff)
                }
            }
        }
    }

    override fun createGroup(
        name: String,
        description: String,
        photoUri: String?,
        memberIds: List<String>,
        jackpotGoal: Double?
    ): String {
        // Photos and the jackpot aren't on the server yet, so they are not shared.
        val me = me
        val record = GroupRecord(
            id = RecordJson.newId(),
            name = name.trim(),
            description = description.trim(),
            admin = me,
            members = listOf(me) + memberIds.filter { it != me }.distinct(),
            created = clock()
        )
        write(record)
        return record.id
    }

    override fun sendText(groupId: String, text: String) =
        write(MessageRecord(RecordJson.newId(), groupId, me, text.trim(), clock()))

    override fun sendImage(groupId: String, uri: String) = Unit

    override fun postExpense(
        groupId: String,
        title: String,
        total: Double,
        payer: String,
        method: PayMethod,
        participants: List<String>,
        shares: Map<String, Double>?
    ) {
        val amountOre = (total * 100).roundToLong()
        val now = clock()
        write(
            ExpenseRecord(
                id = RecordJson.newId(),
                group = groupId,
                creator = me,
                title = title.trim(),
                amountOre = amountOre,
                payer = payer,
                method = method.name.lowercase(),
                date = now,
                participants = participants,
                shares = shares?.let { toOre(it, participants, amountOre, payer) },
                created = now
            )
        )
    }

    override fun pay(groupId: String, expenseId: String, personId: String) {
        // Members can only record their own payments; the server enforces the same rule.
        if (personId != me) return
        val g = group(groupId) ?: return
        val expense = g.messages.filterIsInstance<Message.Expense>()
            .firstOrNull { it.id == expenseId } ?: return
        if (personId in expense.paid || personId !in expense.participants) return
        write(
            PaymentRecord(
                id = RecordJson.newId(),
                group = groupId,
                expense = expenseId,
                from = personId,
                to = expense.payer,
                amountOre = (Ledger.share(expense, personId) * 100).roundToLong(),
                created = clock()
            )
        )
    }

    // The jackpot isn't stored on the server yet.
    override fun topUpJackpot(groupId: String, amount: Double) = Unit
    override fun fillJackpot(groupId: String) = Unit
    override fun payOutJackpot(groupId: String, winner: String, prize: Double) = Unit

    // Reads

    override fun person(id: String): Person {
        val user = synchronized(lock) { state.users[id] }
        val name = user?.name?.trim().orEmpty().ifEmpty { user?.phone ?: "Someone" }
        return Person(
            id = id,
            fullName = name,
            shortName = name.substringBefore(' '),
            initials = Ledger.initials(name),
            phone = user?.phone.orEmpty()
        )
    }

    override fun group(id: String): Group? = _groups.value.firstOrNull { it.id == id }

    override fun format(amount: Double): String = formatMoney(amount, _settings.value.currency)

    override fun knownPeople(): List<Person> {
        val me = me
        val ids = _groups.value.flatMap { it.members }.toSet() - me
        return ids.map(::person).sortedBy { it.fullName }
    }

    override suspend fun findUser(phoneOrEmail: String): UserLookup = try {
        val user = remote.findUser(phoneOrEmail.trim())
        if (user == null) {
            UserLookup.NotFound
        } else {
            mutate { s -> s.copy(users = s.users + (user.id to user)) }
            UserLookup.Found(person(user.id))
        }
    } catch (e: IOException) {
        UserLookup.Failed
    }

    override fun markRead(groupId: String) = mutate { s -> s.copy(unread = s.unread - groupId) }

    // State

    private fun mutate(transform: (State) -> State) = synchronized(lock) {
        state = transform(state)
        _groups.value = assemble(state)
    }

    private fun key(record: RemoteRecord) = key(record.collection, record.id)
    private fun key(collection: String, id: String) = "$collection/$id"

    /** Turns the flat server records into the [Group]s the screens show. */
    private fun assemble(s: State): List<Group> {
        val me = s.userId
        val items = s.records.values.filter { it !is GroupRecord }.groupBy(::groupOf)
        val paymentsByExpense = s.records.values.filterIsInstance<PaymentRecord>()
            .groupBy { it.expense }

        fun name(id: String) = s.users[id]?.name?.trim().orEmpty().ifEmpty { "Someone" }
        fun who(id: String) = if (id == me) "You" else name(id).substringBefore(' ')
        fun whom(id: String) = if (id == me) "you" else name(id).substringBefore(' ')

        return s.records.values.filterIsInstance<GroupRecord>().map { g ->
            val timeline = items[g.id].orEmpty().sortedBy(::createdOf)
            val messages = buildList {
                add(Message.System("${g.id}-created", "${who(g.admin)} created the group"))
                if (g.description.isNotBlank()) {
                    add(Message.System("${g.id}-about", g.description))
                }
                timeline.forEach { r ->
                    when (r) {
                        is ExpenseRecord -> add(r.toMessage(paymentsByExpense[r.id].orEmpty()))
                        is PaymentRecord -> add(
                            Message.System(
                                r.id,
                                "${who(r.from)} paid ${whom(r.to)} ${format(r.amountOre / 100.0)}"
                            )
                        )
                        is MessageRecord -> add(Message.Text(r.id, r.from, r.text))
                        is GroupRecord -> Unit
                    }
                }
            }
            val updated = maxOf(g.created, timeline.lastOrNull()?.let(::createdOf) ?: 0)
            Group(
                id = g.id,
                name = g.name,
                initials = Ledger.initials(g.name),
                description = g.description,
                members = g.members,
                createdBy = g.admin,
                updated = updated,
                whenLabel = whenLabel(updated),
                unread = s.unread[g.id] ?: 0,
                messages = messages
            )
        }.sortedByDescending { it.updated }
    }

    private fun createdOf(record: RemoteRecord): Long = when (record) {
        is GroupRecord -> record.created
        is ExpenseRecord -> record.created
        is PaymentRecord -> record.created
        is MessageRecord -> record.created
    }

    private fun ExpenseRecord.toMessage(payments: List<PaymentRecord>): Message.Expense {
        val fractions = shares?.let { s ->
            participants.associateWith { (s[it] ?: 0L).toDouble() / amountOre.coerceAtLeast(1) }
        }
        // Someone whose share is zero (covered by another member) has nothing to pay.
        val covered = participants.filter {
            it != payer &&
                fractions != null &&
                (fractions[it] ?: 0.0) < 1e-9
        }
        return Message.Expense(
            id = id,
            from = creator,
            title = title,
            total = amountOre / 100.0,
            payer = payer,
            method = PayMethod.entries.firstOrNull { it.name.equals(method, true) }
                ?: PayMethod.EVEN,
            participants = participants,
            shares = fractions,
            paid = payments.map { it.from }.toSet() + covered,
            timestamp = created
        )
    }

    private fun whenLabel(millis: Long): String {
        if (millis <= 0) return ""
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = clock() }
        fun Calendar.day() = get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
        val pattern = when (today.day() - then.day()) {
            0 -> "HH:mm"
            1 -> return "Yesterday"
            else -> "d MMM"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    private companion object {
        /** Øre per participant, summing exactly to [total]; rounding goes to the payer. */
        fun toOre(
            fractions: Map<String, Double>,
            participants: List<String>,
            total: Long,
            payer: String
        ): Map<String, Long> {
            val ore = participants.associateWith { ((fractions[it] ?: 0.0) * total).roundToLong() }
                .toMutableMap()
            val diff = total - ore.values.sum()
            val absorb = if (payer in ore) payer else participants.first()
            ore[absorb] = (ore[absorb] ?: 0L) + diff
            return ore
        }
    }
}
