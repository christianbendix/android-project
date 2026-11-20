package com.example.splendit.ui

import com.example.splendit.data.InMemoryGroupRepository
import com.example.splendit.data.model.ME
import com.example.splendit.ui.create.CreateGroupUiState
import com.example.splendit.ui.create.CreateGroupViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateGroupViewModelTest {
    @Test
    fun cannotCreate_withoutNameAndMembers() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val vm = CreateGroupViewModel(repo)
        val before = repo.groups.value.size

        assertNull(vm.create())
        vm.onNameChange("   ")
        vm.toggleFriend("mads")
        assertNull(vm.create())
        vm.onNameChange("Trip")
        vm.toggleFriend("mads") // deselect again
        assertNull(vm.create())

        assertEquals(before, repo.groups.value.size)
    }

    @Test
    fun phoneInvite_needsEightDigits_andIsNotAddedTwice() = runTest {
        val vm = CreateGroupViewModel(InMemoryGroupRepository(backgroundScope))

        vm.onPhoneChange("1234 567")
        vm.addPhone()
        assertTrue(vm.uiState.value.selected.isEmpty())

        vm.onPhoneChange("12 34 56 78")
        vm.addPhone()
        vm.onPhoneChange("12345678")
        vm.addPhone()

        with(vm.uiState.value) {
            assertEquals(listOf("tel12345678"), selected)
            assertEquals("+45 12 34 56 78", invitedPeople.single().fullName)
            assertEquals("", phone)
        }
    }

    @Test
    fun friendSearch_filtersByName_andMarksSelected() = runTest {
        val vm = CreateGroupViewModel(InMemoryGroupRepository(backgroundScope))
        vm.toggleFriend("sofie")
        vm.onQueryChange("SOF")

        val item = vm.uiState.value.friends.single()
        assertEquals("sofie", item.person.id)
        assertTrue(item.selected)
    }

    @Test
    fun create_savesGroupWithJackpotGoal_andResetsForm() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val vm = CreateGroupViewModel(repo)
        vm.onNameChange("Cabin weekend")
        vm.toggleFriend("mads")
        vm.onGoalChange(1000.0)

        val id = vm.create()

        assertNotNull(id)
        val g = repo.group(id!!)!!
        assertEquals(listOf(ME, "mads"), g.members)
        assertEquals(1000.0, g.jackpot!!.goal, 0.0)
        assertEquals(CreateGroupUiState(), vm.uiState.value)
    }

    @Test
    fun create_withJackpotOff_hasNoJackpot() = runTest {
        val repo = InMemoryGroupRepository(backgroundScope)
        val vm = CreateGroupViewModel(repo)
        vm.onNameChange("No pot")
        vm.toggleFriend("ida")
        vm.toggleJackpot()

        assertFalse(vm.uiState.value.jackpot)
        assertNull(repo.group(vm.create()!!)!!.jackpot)
    }
}
