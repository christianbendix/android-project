package com.example.splendit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.model.Group
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.domain.Ledger
import com.example.splendit.ui.groupRepository
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HomeFilter(val label: String) { ALL("All"), CREATED("Created"), JOINED("Joined") }

enum class BalanceKind { OWED, OWE, SETTLED }

data class RecentBill(
    val groupId: String,
    val groupName: String,
    val title: String,
    val amount: String,
    val status: String,
    val highlight: Boolean
)

data class GroupRow(
    val id: String,
    val name: String,
    val initials: String,
    val photoUri: String?,
    val preview: String,
    val whenLabel: String,
    val hasUnread: Boolean,
    val jackpotTag: String?,
    val balanceLabel: String,
    val balanceKind: BalanceKind
)

data class HomeUiState(
    val netLabel: String = "",
    val owedLabel: String = "",
    val oweLabel: String = "",
    val recent: List<RecentBill> = emptyList(),
    val query: String = "",
    val filter: HomeFilter = HomeFilter.ALL,
    val groups: List<GroupRow> = emptyList(),
    val owingGroupId: String? = null
)

class HomeViewModel(private val repository: GroupRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HomeFilter.ALL)

    val uiState: StateFlow<HomeUiState> = combine(repository.groups, query, filter) { g, q, f ->
        buildState(g, q, f)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildState(repository.groups.value, query.value, filter.value)
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: HomeFilter) {
        filter.value = value
    }

    private fun buildState(groups: List<Group>, q: String, f: HomeFilter): HomeUiState {
        val fmt = repository::format
        val balances = groups.associate { it.id to Ledger.balance(it) }
        val owed = balances.values.filter { it > 0 }.sum()
        val owe = -balances.values.filter { it < 0 }.sum()
        val net = owed - owe

        val recent = groups
            .flatMap { g -> g.messages.filterIsInstance<Message.Expense>().map { g to it } }
            .sortedByDescending { (_, e) -> e.timestamp }
            .take(6)
            .map { (g, e) ->
                val mine = e.payer == ME
                val paid = ME in e.paid || ME !in e.participants
                RecentBill(
                    groupId = g.id,
                    groupName = g.name,
                    title = e.title,
                    amount = fmt(e.total),
                    status = when {
                        mine -> "You paid"
                        paid -> "Settled"
                        else -> "You owe ${fmt(Ledger.share(e, ME))}"
                    },
                    highlight = mine || !paid
                )
            }

        val needle = q.trim().lowercase()
        val rows = groups
            .sortedByDescending { it.updated }
            .filter {
                when (f) {
                    HomeFilter.ALL -> true
                    HomeFilter.CREATED -> it.createdBy == ME
                    HomeFilter.JOINED -> it.createdBy != ME
                }
            }
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .map { g ->
                val b = balances.getValue(g.id)
                GroupRow(
                    id = g.id,
                    name = g.name,
                    initials = g.initials,
                    photoUri = g.photoUri,
                    preview = preview(g),
                    whenLabel = g.whenLabel,
                    hasUnread = g.unread > 0,
                    jackpotTag = g.jackpot?.let {
                        "${(it.pool / it.goal * 100).coerceAtMost(100.0).roundToInt()}%"
                    },
                    balanceLabel = when {
                        abs(b) < 1 -> "Settled"
                        b > 0 -> "+${fmt(b)}"
                        else -> "−${fmt(b)}"
                    },
                    balanceKind = when {
                        b >= 1 -> BalanceKind.OWED
                        b <= -1 -> BalanceKind.OWE
                        else -> BalanceKind.SETTLED
                    }
                )
            }

        return HomeUiState(
            netLabel = (if (net >= 0) "+" else "−") + fmt(net),
            owedLabel = fmt(owed),
            oweLabel = fmt(owe),
            recent = recent,
            query = q,
            filter = f,
            groups = rows,
            owingGroupId = groups.firstOrNull { balances.getValue(it.id) <= -1 }?.id
        )
    }

    private fun preview(g: Group): String {
        val m = g.messages.lastOrNull { it !is Message.System } ?: g.messages.lastOrNull()
        fun who(from: String) = if (from == ME) "You" else repository.person(from).shortName
        return when (m) {
            is Message.Text -> "${who(m.from)}: ${m.text}"
            is Message.Image -> "${who(m.from)} sent a photo"
            is Message.Expense -> "${who(m.from)}: ${m.title} · ${repository.format(m.total)}"
            is Message.System -> m.text
            null -> ""
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(groupRepository) }
        }
    }
}
