package com.example.splendit.ui

import androidx.lifecycle.SavedStateHandle
import com.example.splendit.data.InMemoryGroupRepository
import com.example.splendit.data.model.Message
import com.example.splendit.ui.jackpot.JackpotPhase
import com.example.splendit.ui.jackpot.JackpotViewModel
import com.example.splendit.ui.navigation.Routes
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JackpotViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun vm(repo: InMemoryGroupRepository, groupId: String) =
        JackpotViewModel(repo, SavedStateHandle(mapOf(Routes.ARG_GROUP_ID to groupId)), Random(3))

    @Test
    fun potNotFullCanBeToppedUp() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val vm = vm(repo, "g3")
        val s = vm.uiState.value
        assertEquals(JackpotPhase.IDLE, s.phase)
        assertTrue(s.canTopUp)
        assertFalse(s.canReady)
        assertEquals(
            "660 kr to go. 10% of every settled share is held here until the pot is full.",
            s.status
        )

        vm.topUp()
        assertEquals("390 kr", vm.uiState.value.pool)
    }

    @Test
    fun oddsFavourFasterPayers() = runTest {
        val vm = vm(InMemoryGroupRepository(backgroundScope), "g1")
        val odds = vm.uiState.value.members.associate { it.id to it.odds }
        // Weights are rating squared: 12.25 + 20.25 + 25 + 6.25 = 63.75.
        assertEquals("39%", odds["sofie"])
        assertEquals("10%", odds["emil"])
    }

    @Test
    fun fullPotWaitsForEveryoneThenSpinsAndPaysOut() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val vm = vm(repo, "g3")
        vm.fillDemo()
        runCurrent()
        assertTrue(vm.uiState.value.canReady)

        // The others get ready on their own; the spin still waits for me.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(JackpotPhase.IDLE, vm.uiState.value.phase)
        assertEquals("Waiting for you to be ready in the app", vm.uiState.value.status)

        vm.imReady()
        assertEquals(JackpotPhase.COUNT, vm.uiState.value.phase)
        assertEquals("3", vm.uiState.value.hubText)

        advanceTimeBy(JackpotViewModel.COUNT_MILLIS * 3 + 1)
        runCurrent()
        assertEquals(JackpotPhase.BUSY, vm.uiState.value.phase)

        advanceUntilIdle()
        val done = vm.uiState.value
        assertEquals(JackpotPhase.DONE, done.phase)
        val winner = done.winner!!

        vm.collect()
        val g = repo.group("g3")!!
        assertEquals(0.0, g.jackpot!!.pool, 1e-9)
        val last = g.messages.last() as Message.System
        assertTrue(last.text.contains("won the 1.000 kr jackpot"))
        assertTrue(winner in g.members)
    }
}
