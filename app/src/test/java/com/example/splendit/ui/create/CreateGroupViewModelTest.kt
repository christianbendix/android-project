package com.example.splendit.ui.create

import com.example.splendit.R
import com.example.splendit.data.InMemoryGroupRepository
import com.example.splendit.data.SeedData.ME
import com.example.splendit.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateGroupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = InMemoryGroupRepository()
    private val vm = CreateGroupViewModel(repo)

    @Test
    fun lookupByDanishNumberAddsAndSelectsTheMember() = runTest {
        vm.onLookupChange("22 41 08 19")
        vm.addLookup()
        runCurrent()

        with(vm.uiState.value) {
            assertEquals(listOf("mads"), selected)
            assertEquals("Mads Holm", foundPeople.single().fullName)
            assertEquals("", lookup)
            assertNull(lookupError)
        }
    }

    @Test
    fun unknownNumberShowsAnError() = runTest {
        vm.onLookupChange("99 99 99 99")
        vm.addLookup()
        runCurrent()

        assertEquals(R.string.lookup_not_found, vm.uiState.value.lookupError)
        assertEquals(emptyList<String>(), vm.uiState.value.selected)
    }

    @Test
    fun somethingThatIsNeitherPhoneNorEmailIsRejectedWithoutARequest() = runTest {
        vm.onLookupChange("hello")
        vm.addLookup()

        assertEquals(R.string.lookup_invalid, vm.uiState.value.lookupError)
    }

    @Test
    fun createPassesNameDescriptionAndMembers_withTheJackpotOffByDefault() = runTest {
        vm.onNameChange("Roskilde 2027")
        vm.onDescriptionChange("Tent and food")
        vm.toggleFriend("sofie")

        val id = vm.create()!!

        val g = repo.group(id)!!
        assertEquals("Tent and food", g.description)
        assertEquals(listOf(ME, "sofie"), g.members)
        assertNull(g.jackpot)
    }
}
