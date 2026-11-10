package com.example.splendit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.SeedData
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Person
import com.example.splendit.ui.groupRepository
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProfileUiState(
    val me: Person,
    val groupCount: Int,
    val friends: List<Person>,
    val rating: String,
    val settings: List<Pair<String, String>>
)

class ProfileViewModel(private val repository: GroupRepository) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> =
        combine(repository.groups, repository.settings) { _, _ -> build() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build())

    private fun build(): ProfileUiState {
        val groups = repository.groups.value
        val settings = repository.settings.value
        // "Pay rating" is the average of my jackpot ratings across groups.
        val ratings = groups.mapNotNull { it.jackpot?.ratingOf(ME) }
        return ProfileUiState(
            me = repository.person(ME),
            groupCount = groups.size,
            friends = SeedData.friends.map(repository::person),
            rating = if (ratings.isEmpty()) {
                "–"
            } else {
                String.format(
                    Locale.US,
                    "%.1f",
                    ratings.average()
                )
            },
            settings = listOf(
                "Payment" to "MobilePay ··19",
                "Default game" to settings.defaultGame.label,
                "Notifications" to "On",
                "Currency" to settings.currency.code
            )
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProfileViewModel(groupRepository) }
        }
    }
}
