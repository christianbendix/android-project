package com.example.splendit.data

import com.example.splendit.data.remote.ExpenseRecord
import com.example.splendit.data.remote.GroupRecord
import com.example.splendit.data.remote.GroupRemote
import com.example.splendit.data.remote.HttpException
import com.example.splendit.data.remote.MessageRecord
import com.example.splendit.data.remote.PaymentRecord
import com.example.splendit.data.remote.RemoteChange
import com.example.splendit.data.remote.RemoteRecord
import com.example.splendit.data.remote.Snapshot
import com.example.splendit.data.remote.UserRecord
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An in-process server with switchable failures. Records are visible to everyone (the real
 * access rules are tested on the server side); [push] plays another member's change.
 */
class FakeGroupRemote(val users: List<UserRecord>) : GroupRemote {
    val records = linkedMapOf<String, RemoteRecord>()
    var createCalls = 0

    /** Fail every request with an IOException, as with no network. */
    var offline = false

    /** The next N creates fail; with [lostResponse] the server still keeps the record. */
    var failNextCreates = 0
    var lostResponse = false

    /** Every create is refused with this status, as an access rule would. */
    var rejectWith: Int? = null

    private var live = Channel<RemoteChange>(Channel.UNLIMITED)

    override suspend fun fetchAll(): Snapshot {
        if (offline) throw IOException("offline")
        val all = records.values.toList()
        return Snapshot(
            groups = all.filterIsInstance<GroupRecord>(),
            expenses = all.filterIsInstance<ExpenseRecord>(),
            payments = all.filterIsInstance<PaymentRecord>(),
            messages = all.filterIsInstance<MessageRecord>(),
            users = users
        )
    }

    override suspend fun fetchUsers(ids: Collection<String>): List<UserRecord> {
        if (offline) throw IOException("offline")
        return users.filter { it.id in ids }
    }

    override suspend fun findUser(phoneOrEmail: String): UserRecord? {
        if (offline) throw IOException("offline")
        return users.firstOrNull { it.phone == phoneOrEmail || it.email == phoneOrEmail }
    }

    override suspend fun create(record: RemoteRecord) {
        createCalls++
        if (offline) throw IOException("offline")
        rejectWith?.let { throw HttpException(it) }
        if (failNextCreates > 0) {
            failNextCreates--
            if (lostResponse) store(record)
            throw IOException("timeout")
        }
        store(record)
    }

    /** Another member's change: stored, then pushed to the realtime stream. */
    fun push(record: RemoteRecord) = store(record)

    /** Stores without telling the realtime stream, as if it happened while disconnected. */
    fun storeQuietly(record: RemoteRecord) {
        records[record.id] = record
    }

    private fun store(record: RemoteRecord) {
        // A repeated id is the same record: PocketBase rejects the duplicate.
        if (record.id in records) return
        records[record.id] = record
        live.trySend(RemoteChange.Upsert(record))
    }

    /** Drops the realtime connection; the next [changes] call reconnects. */
    fun disconnect() {
        live.close(IOException("connection lost"))
        live = Channel(Channel.UNLIMITED)
    }

    override fun changes(): Flow<RemoteChange> = flow {
        if (offline) throw IOException("offline")
        val channel = live
        emit(RemoteChange.Connected)
        for (change in channel) emit(change)
        throw IOException("connection closed")
    }
}
