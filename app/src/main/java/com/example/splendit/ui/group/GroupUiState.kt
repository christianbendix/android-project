package com.example.splendit.ui.group

import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.Person
import com.example.splendit.data.model.SplitMode
import com.example.splendit.domain.ExpenseDraft

/** One row of the group chat, already formatted for display. */
sealed interface ChatItem {
    val id: Long

    data class System(override val id: Long, val text: String) : ChatItem

    data class Bubble(
        override val id: Long,
        val mine: Boolean,
        val sender: String?,
        val text: String
    ) : ChatItem

    data class Photo(
        override val id: Long,
        val mine: Boolean,
        val sender: String?,
        val uri: String?
    ) : ChatItem

    data class Bill(
        override val id: Long,
        val mine: Boolean,
        val sender: String?,
        val method: String,
        val title: String,
        val total: String,
        val payerInitials: String,
        val payerLine: String,
        val shareLine: String,
        val status: String,
        /** Non-null when I can pay my share from the card. */
        val payLabel: String?,
        /** Non-null when the bill is done from my point of view. */
        val doneLabel: String?
    ) : ChatItem
}

enum class BalanceTone { OWE, OWED, SETTLED }

data class JackpotBanner(
    val label: String,
    val progress: Float,
    val subtitle: String,
    val full: Boolean
)

data class SplitRow(
    val id: String,
    val initials: String,
    val name: String,
    val subtitle: String,
    val highlight: Boolean,
    val canCover: Boolean,
    val inputText: String,
    val sliderMax: Float,
    val sliderSteps: Int,
    val sliderValue: Float
)

data class ParticipantChip(
    val id: String,
    val initials: String,
    val name: String,
    val selected: Boolean
)

data class ExpenseSheetUiState(
    val draft: ExpenseDraft,
    val currencyLabel: String,
    val amountHint: String,
    val chips: List<ParticipantChip>,
    val allLabel: String,
    val splitRows: List<SplitRow>,
    val unitLabel: String,
    val splitSummary: String,
    val splitBalanced: Boolean,
    val cashbackHint: String?,
    val continueLabel: String,
    val canContinue: Boolean
) {
    val showSplitRows: Boolean get() = draft.split != SplitMode.EQUAL &&
        draft.participants.isNotEmpty()
}

enum class GamePhase { READY, BUSY, DONE }

data class PlayingCard(val value: Int, val suit: Char) {
    val rank: String
        get() = when (value) {
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            14 -> "A"
            else -> value.toString()
        }
}

data class CardUi(val name: String, val card: PlayingCard?, val loser: Boolean)

data class OweLine(val initials: String, val line: String, val amount: String)

data class GameUiState(
    val type: PayMethod,
    val phase: GamePhase,
    val title: String,
    val subtitle: String,
    val participants: List<String>,
    val wheelLabels: List<String>,
    val rotation: Float,
    val winner: String?,
    val winnerInitials: String,
    val winnerLine: String,
    val winnerSubtitle: String,
    val cards: List<CardUi>,
    val owes: List<OweLine>
)

data class GroupUiState(
    val found: Boolean = false,
    val name: String = "",
    val initials: String = "",
    val photoUri: String? = null,
    val memberNames: String = "",
    val memberStack: List<Person> = emptyList(),
    val balanceText: String = "",
    val balanceTone: BalanceTone = BalanceTone.SETTLED,
    val settleLabel: String? = null,
    val jackpot: JackpotBanner? = null,
    val items: List<ChatItem> = emptyList(),
    val chatText: String = "",
    val sheet: ExpenseSheetUiState? = null,
    val game: GameUiState? = null
)
