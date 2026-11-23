package com.example.splendit.data.remote

import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Server records, one class per PocketBase collection (see `pb_migrations/`). Amounts are whole
 * øre so the server never stores floating point money. Times are epoch millis.
 */
sealed interface RemoteRecord {
    val id: String
    val collection: String
}

data class UserRecord(val id: String, val name: String, val phone: String, val email: String = "")

data class GroupRecord(
    override val id: String,
    val name: String,
    val description: String,
    val admin: String,
    val members: List<String>,
    val created: Long
) : RemoteRecord {
    override val collection get() = GROUPS
}

data class ExpenseRecord(
    override val id: String,
    val group: String,
    val creator: String,
    val title: String,
    val amountOre: Long,
    val payer: String,
    /** `even`, `wheel` or `card`. */
    val method: String,
    val date: Long,
    val participants: List<String>,
    /** Øre per participant; null means an equal split. */
    val shares: Map<String, Long>?,
    val created: Long
) : RemoteRecord {
    override val collection get() = EXPENSES
}

data class PaymentRecord(
    override val id: String,
    val group: String,
    val expense: String,
    val from: String,
    val to: String,
    val amountOre: Long,
    val created: Long
) : RemoteRecord {
    override val collection get() = PAYMENTS
}

data class MessageRecord(
    override val id: String,
    val group: String,
    val from: String,
    val text: String,
    val created: Long
) : RemoteRecord {
    override val collection get() = MESSAGES
}

const val USERS = "users"
const val GROUPS = "groups"
const val EXPENSES = "expenses"
const val PAYMENTS = "payments"
const val MESSAGES = "messages"

/** Everything the signed-in user can read, as returned by a full reload. */
data class Snapshot(
    val groups: List<GroupRecord> = emptyList(),
    val expenses: List<ExpenseRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val messages: List<MessageRecord> = emptyList(),
    val users: List<UserRecord> = emptyList()
) {
    val records: List<RemoteRecord> get() = groups + expenses + payments + messages
}

/** Converts between [RemoteRecord]s and PocketBase's JSON. */
object RecordJson {
    private val random = SecureRandom()
    private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

    /**
     * PocketBase ids are 15 characters of `[a-z0-9]`. The client picks the id before the first
     * attempt, so a retried create can't make a duplicate.
     */
    fun newId(): String = (1..15).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }
        .joinToString("")

    fun user(json: JSONObject) = UserRecord(
        id = json.getString("id"),
        name = json.optString("name"),
        phone = json.optString("phone"),
        email = json.optString("email")
    )

    /** Parses a record from any of the four group collections; null for anything else. */
    fun record(json: JSONObject): RemoteRecord? = when (json.optString("collectionName")) {
        GROUPS -> GroupRecord(
            id = json.getString("id"),
            name = json.optString("name"),
            description = json.optString("description"),
            admin = json.optString("admin"),
            members = json.optJSONArray("members").strings(),
            created = time(json.optString("created"))
        )

        EXPENSES -> ExpenseRecord(
            id = json.getString("id"),
            group = json.optString("group"),
            creator = json.optString("creator"),
            title = json.optString("title"),
            amountOre = json.optLong("amount_ore"),
            payer = json.optString("payer"),
            method = json.optString("method"),
            date = time(json.optString("date")),
            participants = json.optJSONArray("participants").strings(),
            shares = json.optJSONObject("shares")?.let { s ->
                s.keys().asSequence().associateWith { s.optLong(it) }
            },
            created = time(json.optString("created"))
        )

        PAYMENTS -> PaymentRecord(
            id = json.getString("id"),
            group = json.optString("group"),
            expense = json.optString("expense"),
            from = json.optString("from"),
            to = json.optString("to"),
            amountOre = json.optLong("amount_ore"),
            created = time(json.optString("created"))
        )

        MESSAGES -> MessageRecord(
            id = json.getString("id"),
            group = json.optString("group"),
            from = json.optString("from"),
            text = json.optString("text"),
            created = time(json.optString("created"))
        )

        else -> null
    }

    /** The body for a create request. Server-set fields (`created`) are left out. */
    fun body(record: RemoteRecord): JSONObject = when (record) {
        is GroupRecord -> JSONObject()
            .put("id", record.id)
            .put("name", record.name)
            .put("description", record.description)
            .put("admin", record.admin)
            .put("members", JSONArray(record.members))

        is ExpenseRecord -> JSONObject()
            .put("id", record.id)
            .put("group", record.group)
            .put("creator", record.creator)
            .put("title", record.title)
            .put("amount_ore", record.amountOre)
            .put("payer", record.payer)
            .put("method", record.method)
            .put("date", format(record.date))
            .put("participants", JSONArray(record.participants))
            .put("shares", record.shares?.let { JSONObject(it) } ?: JSONObject.NULL)

        is PaymentRecord -> JSONObject()
            .put("id", record.id)
            .put("group", record.group)
            .put("expense", record.expense)
            .put("from", record.from)
            .put("to", record.to)
            .put("amount_ore", record.amountOre)

        is MessageRecord -> JSONObject()
            .put("id", record.id)
            .put("group", record.group)
            .put("from", record.from)
            .put("text", record.text)
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    // PocketBase writes dates as "2026-11-23 14:15:46.123Z", always UTC.
    private fun dateFormat(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun time(text: String): Long {
        if (text.isBlank()) return 0
        for (pattern in listOf("yyyy-MM-dd HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss'Z'")) {
            try {
                return dateFormat(pattern).parse(text)?.time ?: continue
            } catch (e: ParseException) {
                continue
            }
        }
        return 0
    }

    fun format(millis: Long): String = dateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'").format(Date(millis))
}
