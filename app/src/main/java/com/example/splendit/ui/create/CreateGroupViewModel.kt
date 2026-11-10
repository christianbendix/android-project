package com.example.splendit.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.SeedData
import com.example.splendit.data.model.Person
import com.example.splendit.ui.groupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class InviteTab(val label: String) { FRIENDS("Friends"), PHONE("Phone number") }

data class FriendItem(val person: Person, val selected: Boolean)

data class CreateGroupUiState(
    val name: String = "",
    val photoUri: String? = null,
    val selected: List<String> = emptyList(),
    val query: String = "",
    val phone: String = "",
    val tab: InviteTab = InviteTab.FRIENDS,
    val addExpenseFirst: Boolean = false,
    val jackpot: Boolean = true,
    val goal: Double = 500.0,
    /** People invited by phone number in this form; they are not friends yet. */
    val invites: Map<String, Person> = emptyMap()
) {
    val phoneDigits: String get() = phone.filter(Char::isDigit)
    val phoneValid: Boolean get() = phoneDigits.length >= 8
    val canCreate: Boolean get() = name.isNotBlank() && selected.isNotEmpty()

    val friends: List<FriendItem>
        get() {
            val q = query.trim().lowercase()
            return SeedData.friends
                .map { SeedData.people.getValue(it) }
                .filter { q.isEmpty() || it.fullName.lowercase().contains(q) }
                .map { FriendItem(it, it.id in selected) }
        }

    val selectedPeople: List<Person>
        get() = selected.map { invites[it] ?: SeedData.people.getValue(it) }

    val invitedPeople: List<Person> get() = selected.mapNotNull { invites[it] }
}

class CreateGroupViewModel(private val repository: GroupRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    val goalOptions = listOf(250.0, 500.0, 1000.0)
    fun format(amount: Double) = repository.format(amount)

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onPhotoPicked(uri: String?) = _uiState.update { it.copy(photoUri = uri ?: it.photoUri) }
    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }
    fun onPhoneChange(value: String) = _uiState.update { it.copy(phone = value) }
    fun onTabChange(tab: InviteTab) = _uiState.update { it.copy(tab = tab) }
    fun toggleJackpot() = _uiState.update { it.copy(jackpot = !it.jackpot) }
    fun onGoalChange(goal: Double) = _uiState.update { it.copy(goal = goal) }
    fun toggleAddExpenseFirst() = _uiState.update { it.copy(addExpenseFirst = !it.addExpenseFirst) }

    fun toggleFriend(id: String) = _uiState.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun remove(id: String) = _uiState.update { it.copy(selected = it.selected - id) }

    fun addPhone() = _uiState.update { s ->
        if (!s.phoneValid) return@update s
        val digits = s.phoneDigits
        val id = "tel$digits"
        val pretty = "+45 " + digits.chunked(2).joinToString(" ")
        s.copy(
            phone = "",
            invites = s.invites + (id to Person(id, pretty, pretty, "#", pretty)),
            selected = if (id in s.selected) s.selected else s.selected + id
        )
    }

    /** Returns the new group's id, or null if the form is not complete. */
    fun create(): String? {
        val s = _uiState.value
        if (!s.canCreate) return null
        val id = repository.createGroup(
            name = s.name,
            photoUri = s.photoUri,
            memberIds = s.selected,
            invites = s.invitedPeople,
            jackpotGoal = if (s.jackpot) s.goal else null
        )
        _uiState.value = CreateGroupUiState()
        return id
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CreateGroupViewModel(groupRepository) }
        }
    }
}
