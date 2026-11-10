package com.example.splendit.data.model

/** The signed-in user always has this id. Everyone else is a friend or an SMS invite. */
const val ME = "me"

data class Person(
    val id: String,
    val fullName: String,
    val shortName: String,
    val initials: String,
    val phone: String
)

/** How the payer of a bill is chosen. */
enum class PayMethod(val label: String, val description: String) {
    WHEEL("Spin the wheel", "The wheel picks one person to pay it all"),
    CARD("High card", "Everyone draws a card — lowest pays"),
    EVEN("I paid — split evenly", "Everyone sends you their share")
}

enum class SplitMode { EQUAL, PERCENT, AMOUNT }

enum class Currency(val code: String) { DKK("DKK"), EUR("EUR") }

/**
 * A shared pot. Every settled share pays [rate] of itself into [pool]; when the pool reaches
 * [goal] the group spins a wheel weighted by each member's pay [ratings].
 */
data class Jackpot(
    val goal: Double,
    val pool: Double,
    val rate: Double = 0.1,
    val ratings: Map<String, Double>
) {
    val isFull: Boolean get() = pool >= goal
    fun ratingOf(personId: String): Double = ratings[personId] ?: 3.0
}

sealed interface Message {
    val id: Long

    data class System(override val id: Long, val text: String) : Message

    data class Text(override val id: Long, val from: String, val text: String) : Message

    /** [uri] is null for the bundled demo photo. */
    data class Image(override val id: Long, val from: String, val uri: String?) : Message

    data class Expense(
        override val id: Long,
        val from: String,
        val title: String,
        val total: Double,
        val payer: String,
        val method: PayMethod,
        val participants: List<String>,
        /** Fraction of [total] per participant. Null means an equal split. */
        val shares: Map<String, Double>? = null,
        val paid: Set<String> = emptySet(),
        val timestamp: Long
    ) : Message
}

data class Group(
    val id: String,
    val name: String,
    val initials: String,
    val photoUri: String? = null,
    val members: List<String>,
    val createdBy: String,
    val updated: Long,
    val whenLabel: String,
    val unread: Int = 0,
    val jackpot: Jackpot? = null,
    val messages: List<Message>
)

data class AppSettings(
    val currency: Currency = Currency.DKK,
    val defaultGame: PayMethod = PayMethod.WHEEL
)
