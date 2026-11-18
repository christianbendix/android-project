package com.example.splendit.ui.jackpot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.ME
import com.example.splendit.domain.Wheel
import com.example.splendit.ui.groupRepository
import com.example.splendit.ui.navigation.Routes
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JackpotPhase { IDLE, COUNT, BUSY, DONE }

data class JackpotSession(
    val phase: JackpotPhase = JackpotPhase.IDLE,
    val online: Set<String> = setOf(ME),
    val ready: Set<String> = emptySet(),
    val lobby: Boolean = false,
    val count: Int = 3,
    val rotation: Float = 0f,
    val winner: String? = null,
    val prize: Double = 0.0
)

data class JackpotMember(
    val id: String,
    val initials: String,
    val name: String,
    val rating: Double,
    val odds: String,
    val readyLabel: String,
    val ready: Boolean,
    val online: Boolean
)

data class JackpotUiState(
    val found: Boolean = false,
    val groupName: String = "",
    val phase: JackpotPhase = JackpotPhase.IDLE,
    val progress: Float = 0f,
    val kicker: String = "",
    val pool: String = "",
    val goal: String = "",
    val status: String = "",
    val canTopUp: Boolean = false,
    val topUpLabel: String = "",
    val canReady: Boolean = false,
    val winnerInitials: String = "",
    val winnerLine: String = "",
    val winnerSubtitle: String = "",
    val members: List<JackpotMember> = emptyList(),
    val wheelIds: List<String> = emptyList(),
    val wheelLabels: List<String> = emptyList(),
    val wheelWeights: List<Double> = emptyList(),
    val rotation: Float = 0f,
    val winner: String? = null,
    val hubText: String = "★"
)

/**
 * The jackpot lobby. Once the pot is full, the other members (simulated) come online and get
 * ready; when everyone is ready the wheel counts down and spins with odds weighted by rating.
 */
class JackpotViewModel(
    private val repository: GroupRepository,
    savedStateHandle: SavedStateHandle,
    private val random: Random = Random.Default
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[Routes.ARG_GROUP_ID])

    private val session = MutableStateFlow(initialSession())

    val uiState: StateFlow<JackpotUiState> = combine(repository.groups, session) { groups, s ->
        groups.firstOrNull { it.id == groupId }?.let { buildState(it, s) } ?: JackpotUiState()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, JackpotUiState())

    init {
        // Open the lobby as soon as the pot is full, whether it filled before or after we got here.
        viewModelScope.launch {
            repository.groups.collect { groups ->
                val jp = groups.firstOrNull { it.id == groupId }?.jackpot ?: return@collect
                if (jp.isFull && !session.value.lobby) openLobby()
            }
        }
    }

    private fun initialSession(): JackpotSession {
        val members = repository.group(groupId)?.members.orEmpty()
        // Every other member starts out "in the app", like the prototype's demo.
        val online = members.filterIndexed { i, id -> id == ME || i % 2 == 1 }.toSet()
        return JackpotSession(online = online)
    }

    private fun openLobby() {
        session.update { it.copy(lobby = true) }
        val others = repository.group(groupId)?.members.orEmpty().filter { it != ME }
        others.forEachIndexed { i, id ->
            val t = 900L + i * 1300L + random.nextLong(700)
            viewModelScope.launch {
                delay(t / 2)
                session.update { it.copy(online = it.online + id) }
                delay(t - t / 2)
                markReady(id)
            }
        }
    }

    fun imReady() = markReady(ME)

    fun topUp() = repository.topUpJackpot(groupId, TOP_UP)

    fun fillDemo() = repository.fillJackpot(groupId)

    private fun markReady(id: String) {
        session.update { it.copy(ready = it.ready + id) }
        val members = repository.group(groupId)?.members.orEmpty()
        val s = session.value
        if (s.phase == JackpotPhase.IDLE && members.all { it in s.ready }) startCountdown()
    }

    private fun startCountdown() {
        session.update { it.copy(phase = JackpotPhase.COUNT, count = 3) }
        viewModelScope.launch {
            while (session.value.count > 1) {
                delay(COUNT_MILLIS)
                session.update { it.copy(count = it.count - 1) }
            }
            delay(COUNT_MILLIS)
            spin()
        }
    }

    private fun spin() {
        val g = repository.group(groupId) ?: return
        val jp = g.jackpot ?: return
        val weights = Wheel.jackpotWeights(g.members.map(jp::ratingOf))
        val index = Wheel.pickIndex(weights, random.nextDouble())
        session.update {
            it.copy(
                phase = JackpotPhase.BUSY,
                rotation = Wheel.targetRotation(
                    it.rotation,
                    weights,
                    index,
                    random.nextDouble() - 0.5
                ),
                winner = g.members[index],
                prize = jp.pool
            )
        }
        viewModelScope.launch {
            delay(SPIN_MILLIS)
            session.update { it.copy(phase = JackpotPhase.DONE) }
        }
    }

    /** Pays the pot out to the winner and resets it. */
    fun collect() {
        val s = session.value
        val winner = s.winner ?: return
        repository.payOutJackpot(groupId, winner, s.prize)
    }

    private fun name(id: String) = if (id == ME) "You" else repository.person(id).shortName

    private fun buildState(g: Group, s: JackpotSession): JackpotUiState {
        val jp = g.jackpot ?: return JackpotUiState()
        val fmt = repository::format
        val weights = Wheel.jackpotWeights(g.members.map(jp::ratingOf))
        val total = weights.sum()
        val full = jp.isFull
        val notReady = g.members.filter { it !in s.ready }.map { if (it == ME) "you" else name(it) }
        val winner = s.winner?.let(repository::person)
        val winnerOdds = s.winner?.let {
            (weights[g.members.indexOf(it)] / total * 100).roundToInt()
        }
        return JackpotUiState(
            found = true,
            groupName = g.name,
            phase = s.phase,
            progress = (jp.pool / jp.goal).toFloat().coerceIn(0f, 1f),
            kicker = if (full) "Pot is full" else "In the pot",
            pool = fmt(jp.pool),
            goal = fmt(jp.goal),
            status = when (s.phase) {
                JackpotPhase.COUNT -> "Everyone is here. Spinning in…"
                JackpotPhase.BUSY -> "Bigger slices for faster payers…"
                JackpotPhase.DONE -> ""
                JackpotPhase.IDLE -> when {
                    !full -> "${fmt(
                        jp.goal - jp.pool
                    )} to go. ${(jp.rate * 100).roundToInt()}% of " +
                        "every settled share is held here until the pot is full."

                    notReady.isNotEmpty() -> "Waiting for ${notReady.joinToString(
                        ", "
                    )} to be ready in the app"

                    else -> "Everyone is ready"
                }
            },
            canTopUp = !full && s.phase == JackpotPhase.IDLE,
            topUpLabel = fmt(TOP_UP),
            canReady = full && s.phase == JackpotPhase.IDLE && ME !in s.ready,
            winnerInitials = winner?.initials.orEmpty(),
            winnerLine = if (s.winner == ME) {
                "You won ${fmt(s.prize)}"
            } else {
                "${winner?.shortName} won ${fmt(s.prize)}"
            },
            winnerSubtitle = winnerOdds?.let { "$it% odds · paid out via MobilePay" }.orEmpty(),
            members = g.members.mapIndexed { i, id ->
                val p = repository.person(id)
                JackpotMember(
                    id = id,
                    initials = p.initials,
                    name = if (id == ME) "You" else p.fullName,
                    rating = jp.ratingOf(id),
                    odds = "${(weights[i] / total * 100).roundToInt()}%",
                    readyLabel = when {
                        !full -> ""
                        id in s.ready -> "Ready"
                        id in s.online -> "In the app"
                        else -> "Away"
                    },
                    ready = id in s.ready,
                    online = id in s.online
                )
            },
            wheelIds = g.members,
            wheelLabels = g.members.map(::name),
            wheelWeights = weights,
            rotation = s.rotation,
            winner = s.winner,
            hubText = when (s.phase) {
                JackpotPhase.COUNT -> s.count.toString()
                JackpotPhase.DONE -> winner?.initials.orEmpty()
                else -> "★"
            }
        )
    }

    companion object {
        const val TOP_UP = 50.0
        const val COUNT_MILLIS = 900L
        const val SPIN_MILLIS = 5_600L

        fun ratingText(rating: Double): String = String.format(Locale.US, "%.1f", rating)

        val Factory = viewModelFactory {
            initializer { JackpotViewModel(groupRepository, createSavedStateHandle()) }
        }
    }
}
