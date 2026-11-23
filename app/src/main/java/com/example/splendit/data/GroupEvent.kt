package com.example.splendit.data

/** Something another member did that the signed-in user should hear about. */
sealed interface GroupEvent {
    val groupId: String
    val groupName: String

    data class AddedToGroup(
        override val groupId: String,
        override val groupName: String,
        val by: String
    ) : GroupEvent

    data class NewExpense(
        override val groupId: String,
        override val groupName: String,
        val by: String,
        val title: String,
        /** My share, formatted, e.g. "150 kr". */
        val myShare: String
    ) : GroupEvent

    data class PaymentReceived(
        override val groupId: String,
        override val groupName: String,
        val from: String,
        val amount: String
    ) : GroupEvent
}
