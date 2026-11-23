package com.example.splendit.ui

import com.example.splendit.auth.FakeAuthRepository
import com.example.splendit.auth.Session
import com.example.splendit.data.InMemoryGroupRepository
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
        val vm = ProfileViewModel(InMemoryGroupRepository(), auth)
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
            HomeViewModel(InMemoryGroupRepository(), FakeAuthRepository(session))
        observe(vm.uiState)

        assertEquals("Peter Hansen", vm.uiState.value.userName)
        assertEquals("PH", vm.uiState.value.userInitials)
    }

    @Test
    fun displayName_fallsBackToEmail_whenNameIsBlank() {
        val noName = Session("tok", "id", "jane.doe@example.com", " ")

        assertEquals("jane.doe", noName.displayName)
        assertEquals("J", noName.initials)
    }
}
