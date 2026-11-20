package com.example.splendit.ui

import com.example.splendit.auth.FakeAuthRepository
import com.example.splendit.auth.Session
import com.example.splendit.data.InMemoryGroupRepository
import com.example.splendit.ui.home.BalanceKind
import com.example.splendit.ui.home.HomeFilter
import com.example.splendit.ui.home.HomeViewModel
import com.example.splendit.ui.profile.ProfileViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Home and Profile show the signed-in account; Profile is where the user logs out. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val session = Session("tok", "qqw7x1psxmeuq72", "peter@example.com", "Peter Hansen")

    // The UI states use WhileSubscribed, so keep a collector alive like the screen would.
    private fun TestScope.observe(state: StateFlow<*>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
    }

    @Test
    fun profileShowsSignedInUser_andLogoutClearsSession() = runTest {
        val auth = FakeAuthRepository(session)
        val vm = ProfileViewModel(InMemoryGroupRepository(backgroundScope), auth)
        observe(vm.uiState)

        with(vm.uiState.value) {
            assertEquals("Peter Hansen", name)
            assertEquals("peter@example.com", email)
            assertEquals("PH", initials)
        }

        vm.logout()

        assertNull(auth.session.value)
    }

    @Test
    fun homeGreetsSignedInUser() = runTest {
        val vm =
            HomeViewModel(InMemoryGroupRepository(backgroundScope), FakeAuthRepository(session))
        observe(vm.uiState)

        assertEquals("Peter Hansen", vm.uiState.value.userName)
        assertEquals("PH", vm.uiState.value.userInitials)
    }

    @Test
    fun homeFiltersCreatedJoinedAndSearch() = runTest {
        val vm =
            HomeViewModel(InMemoryGroupRepository(backgroundScope), FakeAuthRepository(session))
        observe(vm.uiState)
        fun ids() = vm.uiState.value.groups.map { it.id }.toSet()

        vm.onFilterChange(HomeFilter.CREATED)
        assertEquals(setOf("g2", "g3"), ids())
        vm.onFilterChange(HomeFilter.JOINED)
        assertEquals(setOf("g1", "g4"), ids())
        vm.onFilterChange(HomeFilter.ALL)
        vm.onQueryChange("  LISBON ")
        assertEquals(setOf("g3"), ids())
    }

    @Test
    fun homeOwingGroup_isOneTheUserOwesIn() = runTest {
        val vm =
            HomeViewModel(InMemoryGroupRepository(backgroundScope), FakeAuthRepository(session))
        observe(vm.uiState)

        val state = vm.uiState.value
        val owing = checkNotNull(state.owingGroupId) { "seed data has a group the user owes in" }
        assertEquals(BalanceKind.OWE, state.groups.first { it.id == owing }.balanceKind)
        assertTrue(state.recent.size <= 6)
    }

    @Test
    fun displayName_fallsBackToEmail_whenNameIsBlank() {
        val noName = Session("tok", "id", "jane.doe@example.com", " ")

        assertEquals("jane.doe", noName.displayName)
        assertEquals("J", noName.initials)
    }
}
