package com.example.splendit.domain

import com.example.splendit.data.model.Currency
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.Jackpot
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formats amounts the way the prototype does: rounded, Danish grouping, no sign. */
fun formatMoney(amount: Double, currency: Currency): String {
    val value = NumberFormat.getIntegerInstance(Locale.forLanguageTag("da-DK"))
        .format(abs(amount).roundToLong())
    return if (currency == Currency.EUR) "€$value" else "$value kr"
}

/** Debt calculation: who owes what inside a group. Pure functions, no Android types. */
object Ledger {
    fun share(expense: Message.Expense, personId: String): Double = expense.shares
        ?.let { expense.total * (it[personId] ?: 0.0) }
        ?: (expense.total / expense.participants.size)

    /** Positive: the group owes [me]. Negative: [me] owes the group. */
    fun balance(group: Group, me: String = ME): Double = group.messages
        .filterIsInstance<Message.Expense>()
        .sumOf { e ->
            when {
                e.payer == me ->
                    e.participants
                        .filter { it != me && it !in e.paid }
                        .sumOf { share(e, it) }

                me in e.participants && me !in e.paid -> -share(e, me)

                else -> 0.0
            }
        }

    fun unpaidByMe(group: Group, me: String = ME): List<Message.Expense> = group.messages
        .filterIsInstance<Message.Expense>()
        .filter { it.payer != me && me in it.participants && me !in it.paid }

    /** Rating bonus by pay order: first payer +0.4, second +0.15, third 0, later -0.2. */
    private val ratingDelta = listOf(0.4, 0.15, 0.0, -0.2)

    data class PaymentResult(
        val group: Group,
        val share: Double,
        val cashback: Double,
        val first: Boolean
    )

    /**
     * Marks [personId]'s share of expense [messageId] paid. Returns null when there is nothing
     * to pay (unknown bill or already paid). With a jackpot on, the cashback goes into the pool
     * and the payer's rating moves according to how quickly they paid.
     */
    fun pay(group: Group, messageId: Long, personId: String): PaymentResult? {
        val expense = group.messages.filterIsInstance<Message.Expense>()
            .firstOrNull { it.id == messageId } ?: return null
        if (personId in expense.paid || personId !in expense.participants) return null
        val share = share(expense, personId)
        val position = expense.participants.count { it != expense.payer && it in expense.paid }
        val jackpot = group.jackpot
        var cashback = 0.0
        val newJackpot: Jackpot? = jackpot?.let {
            val delta = ratingDelta[position.coerceAtMost(ratingDelta.lastIndex)]
            val rating = (it.ratingOf(personId) + delta).coerceIn(1.0, 5.0)
            cashback = share * it.rate
            it.copy(pool = it.pool + cashback, ratings = it.ratings + (personId to rating))
        }
        val messages = group.messages.map {
            if (it.id == messageId) expense.copy(paid = expense.paid + personId) else it
        }
        return PaymentResult(
            group = group.copy(messages = messages, jackpot = newJackpot),
            share = share,
            cashback = cashback,
            first = position == 0
        )
    }

    fun initials(name: String): String = name.trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
        .take(2)
        .uppercase()
}
