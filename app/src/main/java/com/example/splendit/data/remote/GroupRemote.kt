package com.example.splendit.data.remote

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

sealed interface RemoteChange {
    /** (Re)connected: reload, since changes may have been missed while disconnected. */
    data object Connected : RemoteChange

    data class Upsert(val record: RemoteRecord) : RemoteChange
    data class Deleted(val collection: String, val id: String) : RemoteChange
}

/**
 * The server as seen by [com.example.splendit.data.RemoteGroupRepository]. Implemented over
 * PocketBase; tests use a fake. Every call throws [IOException] when the request fails
 * ([HttpException] when the server answered with an error).
 */
interface GroupRemote {
    /** Everything the signed-in user can read, including the users in their groups. */
    suspend fun fetchAll(): Snapshot

    suspend fun fetchUsers(ids: Collection<String>): List<UserRecord>

    /** Exact match on phone (with country code) or email; null if nobody has it. */
    suspend fun findUser(phoneOrEmail: String): UserRecord?

    /** Creates [record] with its client-chosen id. Creating the same id again is a no-op. */
    suspend fun create(record: RemoteRecord)

    /** Live changes. Emits [RemoteChange.Connected] first; throws when the connection drops. */
    fun changes(): Flow<RemoteChange>
}

class PocketBaseGroupRemote(
    private val api: PocketBaseDataApi,
    private val realtime: PocketBaseRealtime,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : GroupRemote {

    override suspend fun fetchAll(): Snapshot = withContext(io) {
        val groups = api.list(GROUPS).mapNotNull { RecordJson.record(it) as? GroupRecord }
        val snapshot = Snapshot(
            groups = groups,
            expenses = api.list(EXPENSES).mapNotNull { RecordJson.record(it) as? ExpenseRecord },
            payments = api.list(PAYMENTS).mapNotNull { RecordJson.record(it) as? PaymentRecord },
            messages = api.list(MESSAGES).mapNotNull { RecordJson.record(it) as? MessageRecord }
        )
        snapshot.copy(users = usersById(groups.flatMap { it.members }.toSet()))
    }

    override suspend fun fetchUsers(ids: Collection<String>): List<UserRecord> =
        withContext(io) { usersById(ids.toSet()) }

    private fun usersById(ids: Set<String>): List<UserRecord> = ids
        .filter { it.matches(ID_PATTERN) }
        .chunked(50)
        .flatMap { chunk ->
            api.list(USERS, chunk.joinToString(" || ") { "id='$it'" }).map(RecordJson::user)
        }

    override suspend fun findUser(phoneOrEmail: String): UserRecord? = withContext(io) {
        val response = api.lookupUser(phoneOrEmail)
        when (response.code) {
            200 -> response.body?.let(RecordJson::user)
            404 -> null
            else -> throw HttpException(response.code)
        }
    }

    override suspend fun create(record: RemoteRecord) = withContext(io) {
        val response = api.create(record.collection, RecordJson.body(record))
        if (response.code in 200..299) return@withContext
        // A retry after a lost response: the record is already there.
        if (response.code == 400 && api.get(record.collection, record.id).code == 200) {
            return@withContext
        }
        throw HttpException(response.code)
    }

    override fun changes(): Flow<RemoteChange> = realtime
        .connect(listOf(GROUPS, EXPENSES, PAYMENTS, MESSAGES))
        .mapNotNull { message ->
            when (message) {
                RealtimeMessage.Connected -> RemoteChange.Connected
                is RealtimeMessage.Event -> {
                    val json = message.record
                    if (message.action == "delete") {
                        RemoteChange.Deleted(json.optString("collectionName"), json.optString("id"))
                    } else {
                        RecordJson.record(json)?.let { RemoteChange.Upsert(it) }
                    }
                }
            }
        }
        .flowOn(io)

    private companion object {
        // Ids go into a filter string, so only accept what PocketBase itself generates.
        val ID_PATTERN = Regex("^[a-z0-9]{1,32}$")
    }
}
