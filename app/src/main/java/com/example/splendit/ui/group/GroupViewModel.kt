package com.example.splendit.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.model.Currency
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.SplitMode
import com.example.splendit.domain.ExpenseDraft
import com.example.splendit.domain.Ledger
import com.example.splendit.domain.Wheel
import com.example.splendit.ui.groupRepository
import com.example.splendit.ui.navigation.Routes
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Raw game state; [GroupViewModel] turns it into [GameUiState]. */
data class GameState(
    val type: PayMethod,
    val draft: ExpenseDraft,
    val fractions: Map<String, Double>,
    val phase: GamePhase,
    val rotation: Float = 0f,
    val winner: String? = null,
    val cards: Map<String, PlayingCard> = emptyMap(),
    val revealed: Int = 0
) {
    val participants: List<String> get() = draft.participants
}

private data class SheetState(val open: Boolean = false, val draft: ExpenseDraft? = null)

class GroupViewModel(
    private val repository: GroupRepository,
    savedStateHandle: SavedStateHandle,
    private val random: Random = Random.Default
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[Routes.ARG_GROUP_ID])

    private val chatText = MutableStateFlow("")
    private val sheet = MutableStateFlow(SheetState())
    private val game = MutableStateFlow<GameState?>(null)
    private var gameJob: Job? = null

    val uiState: StateFlow<GroupUiState> =
        combine(repository.groups, chatText, sheet, game) { groups, text, sh, gm ->
            val g = groups.firstOrNull { it.id == groupId }
            if (g == null) GroupUiState() else buildState(g, text, sh, gm)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupUiState())

    init {
        repository.markRead(groupId)
        if (savedStateHandle.get<Boolean>(Routes.ARG_OPEN_EXPENSE) == true) openSheet()
    }

    private val group: Group? get() = repository.group(groupId)

    // Chat

    fun onChatTextChange(value: String) {
        chatText.value = value
    }

    fun sendText() {
        val text = chatText.value.trim()
        if (text.isEmpty()) return
        repository.sendText(groupId, text)
        chatText.value = ""
    }

    fun sendImage(uri: String) = repository.sendImage(groupId, uri)

    fun pay(messageId: Long) = repository.pay(groupId, messageId, ME)

    fun settleAll() {
        val g = group ?: return
        Ledger.unpaidByMe(g).forEach { repository.pay(groupId, it.id, ME) }
    }

    // Expense sheet

    fun openSheet() {
        val g = group ?: return
        val draft = ExpenseDraft(
            participants = g.members,
            method = repository.settings.value.defaultGame
        )
        sheet.value = SheetState(open = true, draft = draft)
    }

    fun closeSheet() = sheet.update { it.copy(open = false) }

    private fun editDraft(transform: (ExpenseDraft) -> ExpenseDraft) =
        sheet.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun onAmountChange(value: String) =
        editDraft { it.copy(amount = value.filter { c -> c.isDigit() || c == '.' || c == ',' }) }

    fun onTitleChange(value: String) = editDraft { it.copy(title = value) }
    fun toggleParticipant(id: String) = editDraft { it.toggleParticipant(id) }
    fun toggleAllParticipants() = editDraft { d -> group?.let { d.toggleAll(it.members) } ?: d }
    fun onSplitModeChange(mode: SplitMode) = editDraft { it.withSplit(mode) }
    fun onMethodChange(method: PayMethod) = editDraft { it.copy(method = method) }
    fun resetSplit() = editDraft { it.resetSplit() }
    fun coverFor(id: String) = editDraft { it.coverFor(id) }

    fun onSlide(id: String, value: Float) = editDraft { d ->
        d.withShare(id, sliderToFraction(d, value.toDouble()))
    }

    fun onShareTyped(id: String, raw: String) = editDraft { d ->
        val clean = raw.filter { it.isDigit() || it == '.' || it == ',' }
        val v = clean.replace(',', '.').toDoubleOrNull()
        if (v ==
            null
        ) {
            d.copy(typed = d.typed + (id to clean))
        } else {
            d.withShare(id, sliderToFraction(d, v), clean)
        }
    }

    fun onShareFieldBlur(id: String) = editDraft { it.copy(typed = it.typed - id) }

    private fun sliderToFraction(d: ExpenseDraft, v: Double): Double = when {
        d.split == SplitMode.PERCENT -> v / 100
        d.amountValue > 0 -> v / d.amountValue
        else -> 0.0
    }

    // Games

    fun startGame() {
        val d = sheet.value.draft ?: return
        if (!d.isValid) return
        val even = d.method == PayMethod.EVEN
        game.value = GameState(
            type = d.method,
            draft = d,
            fractions = d.fractions(),
            phase = if (even) GamePhase.DONE else GamePhase.READY,
            winner = if (even) ME else null
        )
        sheet.update { it.copy(open = false) }
    }

    fun play() {
        when (game.value?.type) {
            PayMethod.WHEEL -> spin()
            PayMethod.CARD -> deal()
            else -> Unit
        }
    }

    private fun spin() {
        val g = game.value ?: return
        if (g.phase != GamePhase.READY) return
        val weights = g.participants.map { 1.0 }
        val index = Wheel.pickIndex(weights, random.nextDouble())
        val rotation = Wheel.targetRotation(g.rotation, weights, index, random.nextDouble() - 0.5)
        game.value =
            g.copy(phase = GamePhase.BUSY, rotation = rotation, winner = g.participants[index])
        gameJob = viewModelScope.launch {
            delay(SPIN_MILLIS)
            game.update { it?.copy(phase = GamePhase.DONE) }
        }
    }

    private fun deal() {
        val g = game.value ?: return
        if (g.phase != GamePhase.READY) return
        val deck = (2..14).shuffled(random)
        val cards = g.participants.mapIndexed { i, id ->
            id to PlayingCard(deck[i], "♠♥♦♣"[random.nextInt(4)])
        }.toMap()
        // Lowest card pays.
        val loser = g.participants.minBy { cards.getValue(it).value }
        game.value = g.copy(phase = GamePhase.BUSY, cards = cards, revealed = 0, winner = loser)
        gameJob = viewModelScope.launch {
            repeat(g.participants.size) {
                delay(CARD_MILLIS)
                game.update { it?.copy(revealed = it.revealed + 1) }
            }
            delay(CARD_MILLIS)
            game.update { it?.copy(phase = GamePhase.DONE) }
        }
    }

    /** Back from the game to the expense form, as the prototype does. */
    fun closeGame() {
        gameJob?.cancel()
        game.value = null
        sheet.update { it.copy(open = true) }
    }

    fun postExpense() {
        val g = game.value ?: return
        val winner = g.winner ?: return
        val d = g.draft
        repository.postExpense(
            groupId = groupId,
            title = d.title,
            total = d.amountValue,
            payer = winner,
            method = g.type,
            participants = d.participants,
            shares = if (d.split == SplitMode.EQUAL) null else g.fractions
        )
        game.value = null
        sheet.value = SheetState()
    }

    // State mapping

    private fun name(id: String) = if (id == ME) "You" else repository.person(id).shortName

    private fun buildState(g: Group, text: String, sh: SheetState, gm: GameState?): GroupUiState {
        val fmt = repository::format
        val b = Ledger.balance(g)
        val owing = Ledger.unpaidByMe(g)
        val oweTo = owing.map {
            repository.person(it.payer).shortName
        }.distinct().joinToString(" & ")
        val jp = g.jackpot
        return GroupUiState(
            found = true,
            name = g.name,
            initials = g.initials,
            photoUri = g.photoUri,
            memberNames = g.members.joinToString(", ") { name(it) },
            memberStack = g.members.filter { it != ME }.take(3).map(repository::person),
            balanceText = when {
                b <= -1 -> "You owe $oweTo ${fmt(b)}"
                b >= 1 -> "You're owed ${fmt(b)} in this group"
                else -> "Everyone is settled up"
            },
            balanceTone = when {
                b <= -1 -> BalanceTone.OWE
                b >= 1 -> BalanceTone.OWED
                else -> BalanceTone.SETTLED
            },
            settleLabel = if (owing.isNotEmpty()) "Settle ${fmt(b)}" else null,
            jackpot = jp?.let {
                JackpotBanner(
                    label = "${fmt(minOf(it.pool, it.goal))} / ${fmt(it.goal)}",
                    progress = (it.pool / it.goal).toFloat().coerceIn(0f, 1f),
                    subtitle = if (it.isFull) {
                        "Pot is full — tap to get ready for the spin"
                    } else {
                        "${(it.rate * 100).roundToInt()}% cashback from every settled share"
                    },
                    full = it.isFull
                )
            },
            items = chatItems(g),
            chatText = text,
            sheet = if (sh.open && sh.draft != null) sheetState(g, sh.draft) else null,
            game = gm?.let(::gameState)
        )
    }

    private fun chatItems(g: Group): List<ChatItem> {
        val fmt = repository::format
        var previous: String? = null
        return g.messages.map { m ->
            val from = when (m) {
                is Message.System -> null
                is Message.Text -> m.from
                is Message.Image -> m.from
                is Message.Expense -> m.from
            }
            val mine = from == ME
            val sender = if (from != null && !mine && previous != from) name(from) else null
            previous = from
            when (m) {
                is Message.System -> ChatItem.System(m.id, m.text)
                is Message.Text -> ChatItem.Bubble(m.id, mine, sender, m.text)
                is Message.Image -> ChatItem.Photo(m.id, mine, sender, m.uri)
                is Message.Expense -> {
                    val share = Ledger.share(m, ME)
                    val others = m.participants.filter { it != m.payer }
                    val paidCount = others.count { it in m.paid }
                    val payer = repository.person(m.payer)
                    var payLabel: String? = null
                    var doneLabel: String? = null
                    val status = when {
                        m.payer == ME -> {
                            if (paidCount == others.size) {
                                doneLabel = "Settled"
                                "Everyone has paid you"
                            } else {
                                val outstanding = others.filter {
                                    it !in m.paid
                                }.sumOf { Ledger.share(m, it) }
                                "$paidCount of ${others.size} paid · ${fmt(outstanding)} on its way"
                            }
                        }

                        ME !in m.participants -> "You are not on this bill"

                        ME in m.paid -> {
                            doneLabel = "Paid"
                            "You paid ${payer.shortName} ${fmt(share)}"
                        }

                        else -> {
                            payLabel = "Pay ${fmt(share)}"
                            "Send your share to ${payer.shortName}"
                        }
                    }
                    ChatItem.Bill(
                        id = m.id,
                        mine = mine,
                        sender = sender,
                        method = m.method.label.uppercase(),
                        title = m.title,
                        total = fmt(m.total),
                        payerInitials = payer.initials,
                        payerLine = if (m.payer ==
                            ME
                        ) {
                            "You pay the bill"
                        } else {
                            "${payer.shortName} pays the bill"
                        },
                        shareLine = if (m.shares != null) {
                            "${m.participants.size} people · custom split · yours ${fmt(share)}"
                        } else {
                            "${m.participants.size} people · ${fmt(share)} each"
                        },
                        status = status,
                        payLabel = payLabel,
                        doneLabel = doneLabel
                    )
                }
            }
        }
    }

    private fun sheetState(g: Group, d: ExpenseDraft): ExpenseSheetUiState {
        val fmt = repository::format
        val amount = d.amountValue
        val n = d.participants.size
        val pct = d.split == SplitMode.PERCENT
        val fr = d.fractions()
        val eq = 1.0 / n.coerceAtLeast(1)
        val step = when {
            amount <= 500 -> 10
            amount <= 2000 -> 25
            else -> 50
        }
        val rows = d.participants.map { id ->
            val f = fr[id] ?: 0.0
            val p = repository.person(id)
            val extra = f > eq + 0.004
            val less = f < eq - 0.004
            val value = if (pct) fmt(f * amount) else "${(f * 100).roundToInt()}%"
            SplitRow(
                id = id,
                initials = p.initials,
                name = if (id == ME) "You" else p.fullName,
                subtitle = when {
                    id in d.covered -> "Covered by you"
                    extra -> (if (id == ME) "Paying extra" else "Paying more") + " · $value"
                    less -> "Paying less · $value"
                    else -> value
                },
                highlight = id in d.covered || extra,
                canCover = id != ME && ME in d.participants && f > 0.0001,
                inputText =
                d.typed[id] ?: if (pct) decimalText(f * 100, 1) else decimalText(f * amount, 2),
                sliderMax = if (pct) 100f else amount.coerceAtLeast(1.0).toFloat(),
                sliderSteps = if (pct) 19 else ((amount / step).toInt() - 1).coerceAtLeast(0),
                sliderValue = if (pct) (f * 100).toFloat() else (f * amount).toFloat()
            )
        }
        val sum = d.assigned
        val currencyLabel = if (repository.settings.value.currency == Currency.EUR) "EUR" else "kr"
        return ExpenseSheetUiState(
            draft = d,
            currencyLabel = currencyLabel,
            amountHint = when {
                amount <= 0 -> "Enter the total"
                d.split != SplitMode.EQUAL -> "Custom split across $n"
                else -> "${fmt(amount / n)} each across $n"
            },
            chips = g.members.map { id ->
                ParticipantChip(id, repository.person(id).initials, name(id), id in d.participants)
            },
            allLabel = if (d.participants.size == g.members.size) "Clear" else "Select all",
            splitRows = rows,
            unitLabel = if (pct) "%" else currencyLabel,
            splitSummary = if (pct) {
                "${(sum * 100).roundToInt()}% assigned"
            } else {
                "${fmt(sum * amount)} of ${fmt(amount)} assigned"
            },
            splitBalanced = abs(sum - 1) < 0.001,
            cashbackHint = g.jackpot?.let {
                if (amount > 0 && n > 0) {
                    "${fmt(amount / n * it.rate)} cashback per share goes to the jackpot"
                } else {
                    "10% cashback per share goes to the jackpot"
                }
            },
            continueLabel = when (d.method) {
                PayMethod.WHEEL -> "Go to the wheel"
                PayMethod.CARD -> "Deal the cards"
                PayMethod.EVEN -> "Send requests"
            },
            canContinue = d.isValid
        )
    }

    private fun gameState(gm: GameState): GameUiState {
        val fmt = repository::format
        val total = gm.draft.amountValue
        val winner = gm.winner?.let(repository::person)
        val winnerName = if (gm.winner == ME) "you" else winner?.shortName.orEmpty()
        return GameUiState(
            type = gm.type,
            phase = gm.phase,
            title = if (gm.type == PayMethod.EVEN) "Split evenly" else gm.type.label,
            subtitle = "${gm.draft.title} · ${fmt(total)}",
            participants = gm.participants,
            wheelLabels = gm.participants.map(::name),
            rotation = gm.rotation,
            winner = gm.winner,
            winnerInitials = winner?.initials.orEmpty(),
            winnerLine = when {
                gm.type == PayMethod.EVEN -> "You paid"
                gm.winner == ME -> "You pay the bill"
                else -> "${winner?.shortName} pays the bill"
            },
            winnerSubtitle = if (gm.type == PayMethod.EVEN) {
                val how = if (gm.draft.split != SplitMode.EQUAL) {
                    "with a custom split"
                } else {
                    "split ${gm.participants.size} ways"
                }
                "${fmt(total)} $how"
            } else {
                "${fmt(total)} — the rest send their share"
            },
            cards = gm.participants.mapIndexed { i, id ->
                CardUi(
                    name = name(id),
                    card = gm.cards[id]?.takeIf { i < gm.revealed },
                    loser = gm.phase == GamePhase.DONE && id == gm.winner
                )
            },
            owes = gm.participants.filter { it != gm.winner }.map { id ->
                OweLine(
                    initials = repository.person(id).initials,
                    line = "${if (id == ME) "You send" else "${name(id)} sends"} to $winnerName",
                    amount = fmt(total * (gm.fractions[id] ?: 0.0))
                )
            }
        )
    }

    private fun decimalText(value: Double, decimals: Int): String {
        val factor = if (decimals == 1) 10.0 else 100.0
        val rounded = (value * factor).roundToInt() / factor
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    companion object {
        const val SPIN_MILLIS = 4_800L
        const val CARD_MILLIS = 420L

        val Factory = viewModelFactory {
            initializer { GroupViewModel(groupRepository, createSavedStateHandle()) }
        }
    }
}
