package com.example.splendit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.splendit.auth.AuthRepository
import com.example.splendit.auth.Session
import com.example.splendit.data.GroupRepository
import com.example.splendit.data.SeedData
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Person
import com.example.splendit.ui.authRepository
import com.example.splendit.ui.groupRepository
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String,
    val email: String,
    val initials: String,
    val groupCount: Int,
    val friends: List<Person>,
    val rating: String,
    val settings: List<Pair<String, String>>
)

class ProfileViewModel(
    private val repository: GroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    val uiState: StateFlow<ProfileUiState> =
        combine(repository.groups, repository.settings, authRepository.session) { _, _, s ->
            build(s)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(null))

    /** Clearing the session sends the app back to the login screen. */
    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    private fun build(session: Session?): ProfileUiState {
        val groups = repository.groups.value
        val settings = repository.settings.value
        // "Pay rating" is the average of my jackpot ratings across groups.
        val ratings = groups.mapNotNull { it.jackpot?.ratingOf(ME) }
        return ProfileUiState(
            name = session?.displayName.orEmpty(),
            email = session?.email.orEmpty(),
            initials = session?.initials.orEmpty(),
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
            initializer { ProfileViewModel(groupRepository, authRepository) }
        }
    }
}
