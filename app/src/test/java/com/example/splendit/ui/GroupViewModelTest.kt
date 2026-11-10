package com.example.splendit.ui

import androidx.lifecycle.SavedStateHandle
import com.example.splendit.data.InMemoryGroupRepository
import com.example.splendit.data.model.ME
import com.example.splendit.data.model.Message
import com.example.splendit.data.model.PayMethod
import com.example.splendit.data.model.SplitMode
import com.example.splendit.ui.group.ChatItem
import com.example.splendit.ui.group.GamePhase
import com.example.splendit.ui.group.GroupViewModel
import com.example.splendit.ui.navigation.Routes
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.setUp(
        groupId: String,
        openExpense: Boolean = false
    ): Pair<InMemoryGroupRepository, GroupViewModel> {
        val repo = InMemoryGroupRepository(backgroundScope, random = Random(7))
        val handle = SavedStateHandle(
            mapOf(
                Routes.ARG_GROUP_ID to groupId,
                Routes.ARG_OPEN_EXPENSE to openExpense
            )
        )
        return repo to GroupViewModel(repo, handle, Random(42))
    }

    private fun GroupViewModel.fillDraft(method: PayMethod) {
        openSheet()
        onTitleChange("Dinner")
        onAmountChange("400")
        onMethodChange(method)
    }

    @Test
    fun openingTheGroupMarksItRead() = runTest {
        val (repo, _) = setUp("g1")
        assertEquals(0, repo.group("g1")!!.unread)
    }

    @Test
    fun showsWhatIOweAndSettleClearsIt() = runTest {
        val (_, vm) = setUp("g1")
        assertEquals("You owe Mads 210 kr", vm.uiState.value.balanceText)
        assertEquals("Settle 210 kr", vm.uiState.value.settleLabel)

        vm.settleAll()

        assertEquals("Everyone is settled up", vm.uiState.value.balanceText)
        assertNull(vm.uiState.value.settleLabel)
    }

    @Test
    fun sendTextAppendsBubbleAndClearsInput() = runTest {
        val (_, vm) = setUp("g2")
        vm.onChatTextChange("  hello  ")
        vm.sendText()
        val last = vm.uiState.value.items.last()
        assertTrue(last is ChatItem.Bubble)
        assertEquals("hello", (last as ChatItem.Bubble).text)
        assertEquals("", vm.uiState.value.chatText)
    }

    @Test
    fun navArgumentOpensTheExpenseSheet() = runTest {
        val (_, vm) = setUp("g2", openExpense = true)
        assertNotNull(vm.uiState.value.sheet)
    }

    @Test
    fun cannotContinueUntilTheDraftIsComplete() = runTest {
        val (_, vm) = setUp("g2")
        vm.openSheet()
        assertFalse(vm.uiState.value.sheet!!.canContinue)
        vm.startGame()
        assertNull(vm.uiState.value.game)

        vm.fillDraft(PayMethod.WHEEL)
        assertTrue(vm.uiState.value.sheet!!.canContinue)
        assertEquals("100 kr each across 4", vm.uiState.value.sheet!!.amountHint)
    }

    @Test
    fun wheelSpinsThenPostsBillWithTheWinnerAsPayer() = runTest {
        val (repo, vm) = setUp("g2")
        vm.fillDraft(PayMethod.WHEEL)
        vm.startGame()
        assertNull(vm.uiState.value.sheet)
        assertEquals(GamePhase.READY, vm.uiState.value.game!!.phase)

        vm.play()
        val spinning = vm.uiState.value.game!!
        assertEquals(GamePhase.BUSY, spinning.phase)
        assertTrue(spinning.rotation >= 360f * 7)
        val winner = spinning.winner!!

        advanceTimeBy(GroupViewModel.SPIN_MILLIS + 1)
        runCurrent()
        assertEquals(GamePhase.DONE, vm.uiState.value.game!!.phase)
        assertEquals(3, vm.uiState.value.game!!.owes.size)

        vm.postExpense()
        assertNull(vm.uiState.value.game)
        val bill = repo.group("g2")!!.messages.last() as Message.Expense
        assertEquals(winner, bill.payer)
        assertEquals(400.0, bill.total, 0.0)
        assertEquals(PayMethod.WHEEL, bill.method)
    }

    @Test
    fun highCardRevealsOneByOneAndLowestCardPays() = runTest {
        val (_, vm) = setUp("g2")
        vm.fillDraft(PayMethod.CARD)
        vm.startGame()
        vm.play()
        assertEquals(0, vm.uiState.value.game!!.cards.count { it.card != null })

        advanceTimeBy(GroupViewModel.CARD_MILLIS + 1)
        runCurrent()
        assertEquals(1, vm.uiState.value.game!!.cards.count { it.card != null })

        advanceTimeBy(GroupViewModel.CARD_MILLIS * 5)
        runCurrent()
        val game = vm.uiState.value.game!!
        assertEquals(GamePhase.DONE, game.phase)
        val lowest = game.cards.minBy { it.card!!.value }
        assertTrue(lowest.loser)
    }

    @Test
    fun closingTheGameReturnsToTheSheetAndStopsTheSpin() = runTest {
        val (_, vm) = setUp("g2")
        vm.fillDraft(PayMethod.WHEEL)
        vm.startGame()
        vm.play()
        vm.closeGame()
        assertNull(vm.uiState.value.game)
        assertNotNull(vm.uiState.value.sheet)
        advanceTimeBy(GroupViewModel.SPIN_MILLIS + 1)
        runCurrent()
        assertNull(vm.uiState.value.game)
    }

    @Test
    fun splitEvenlyMakesMeThePayerAndKeepsCustomShares() = runTest {
        val (repo, vm) = setUp("g2")
        vm.fillDraft(PayMethod.EVEN)
        vm.onSplitModeChange(SplitMode.PERCENT)
        vm.coverFor("lucas")
        vm.startGame()
        assertEquals(GamePhase.DONE, vm.uiState.value.game!!.phase)
        vm.postExpense()

        val bill = repo.group("g2")!!.messages.last() as Message.Expense
        assertEquals(ME, bill.payer)
        assertEquals(0.5, bill.shares!!.getValue(ME), 1e-9)
        assertTrue("lucas" in bill.paid)
    }
}
