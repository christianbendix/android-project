package com.example.splendit.ui.create

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.R
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.UserLookup
import com.example.splendit.data.model.Person
import com.example.splendit.domain.Phone
import com.example.splendit.ui.groupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InviteTab(val label: String) { FRIENDS("People you know"), PHONE("Phone or email") }

data class FriendItem(val person: Person, val selected: Boolean)

data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val selected: List<String> = emptyList(),
    val query: String = "",
    val lookup: String = "",
    val lookupBusy: Boolean = false,
    @StringRes val lookupError: Int? = null,
    val tab: InviteTab = InviteTab.FRIENDS,
    val addExpenseFirst: Boolean = false,
    // The jackpot isn't shared through the server yet, so it starts off.
    val jackpot: Boolean = false,
    val goal: Double = 500.0,
    /** Everyone who can be picked: people I share a group with, then anyone found by lookup. */
    val people: Map<String, Person> = emptyMap(),
    val known: List<String> = emptyList(),
    val found: List<String> = emptyList()
) {
    val canCreate: Boolean get() = name.isNotBlank() && selected.isNotEmpty()
    val canLookup: Boolean get() = lookup.isNotBlank() && !lookupBusy

    val friends: List<FriendItem>
        get() {
            val q = query.trim().lowercase()
            return known.mapNotNull(people::get)
                .filter { q.isEmpty() || it.fullName.lowercase().contains(q) }
                .map { FriendItem(it, it.id in selected) }
        }

    val selectedPeople: List<Person> get() = selected.mapNotNull(people::get)

    val foundPeople: List<Person> get() = found.mapNotNull(people::get)
}

class CreateGroupViewModel(private val repository: GroupRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    val goalOptions = listOf(250.0, 500.0, 1000.0)
    fun format(amount: Double) = repository.format(amount)

    private fun initialState(): CreateGroupUiState {
        val known = repository.knownPeople()
        return CreateGroupUiState(
            people = known.associateBy { it.id },
            known = known.map { it.id },
            // Nobody to pick from yet: start on the lookup tab.
            tab = if (known.isEmpty()) InviteTab.PHONE else InviteTab.FRIENDS
        )
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onPhotoPicked(uri: String?) = _uiState.update { it.copy(photoUri = uri ?: it.photoUri) }
    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }
    fun onLookupChange(value: String) =
        _uiState.update { it.copy(lookup = value, lookupError = null) }

    fun onTabChange(tab: InviteTab) = _uiState.update { it.copy(tab = tab) }
    fun toggleJackpot() = _uiState.update { it.copy(jackpot = !it.jackpot) }
    fun onGoalChange(goal: Double) = _uiState.update { it.copy(goal = goal) }
    fun toggleAddExpenseFirst() = _uiState.update { it.copy(addExpenseFirst = !it.addExpenseFirst) }

    fun toggleFriend(id: String) = _uiState.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun remove(id: String) = _uiState.update { it.copy(selected = it.selected - id) }

    /** Looks up a registered user by phone or email and adds them to the group. */
    fun addLookup() {
        val s = _uiState.value
        if (!s.canLookup) return
        val key = Phone.lookupKey(s.lookup)
        if (key == null) {
            _uiState.update { it.copy(lookupError = R.string.lookup_invalid) }
            return
        }
        _uiState.update { it.copy(lookupBusy = true, lookupError = null) }
        viewModelScope.launch {
            val result = repository.findUser(key)
            _uiState.update { state ->
                val done = state.copy(lookupBusy = false)
                when (result) {
                    is UserLookup.Found -> {
                        val p = result.person
                        if (p.id == repository.me) {
                            done.copy(lookupError = R.string.lookup_is_you)
                        } else {
                            done.copy(
                                lookup = "",
                                people = state.people + (p.id to p),
                                found = if (p.id in
                                    state.found
                                ) {
                                    state.found
                                } else {
                                    state.found + p.id
                                },
                                selected = if (p.id in state.selected) {
                                    state.selected
                                } else {
                                    state.selected + p.id
                                }
                            )
                        }
                    }

                    UserLookup.NotFound -> done.copy(lookupError = R.string.lookup_not_found)
                    UserLookup.Failed -> done.copy(lookupError = R.string.lookup_failed)
                }
            }
        }
    }

    /** Returns the new group's id, or null if the form is not complete. */
    fun create(): String? {
        val s = _uiState.value
        if (!s.canCreate) return null
        val id = repository.createGroup(
            name = s.name,
            description = s.description,
            photoUri = s.photoUri,
            memberIds = s.selected,
            jackpotGoal = if (s.jackpot) s.goal else null
        )
        _uiState.value = initialState()
        return id
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CreateGroupViewModel(groupRepository) }
        }
    }
}
