package com.example.splendit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule(order = 0)
    val auth = FakeAuthRule(FakeAuthRule.TEST_USER)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /**
     * CI's emulator has a small screen, so items low in a screen's list start off-screen. Scroll
     * the outermost list (the first scrollable in the tree) to the item first.
     */
    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    }

    @Test
    fun homeListsGroupsAndOpensChat() {
        scrollTo("Roskilde 2026")
        compose.onNodeWithText("Roskilde 2026").assertIsDisplayed().performClick()
        scrollTo("Tent is packed. Who is on groceries?")
        compose.onNodeWithText("Tent is packed. Who is on groceries?").assertIsDisplayed()
        compose.onNodeWithText("You owe Mads 210 kr").assertIsDisplayed()
    }

    @Test
    fun bottomBarSwitchesToProfile() {
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Test User").assertIsDisplayed()
        compose.onNodeWithText("test@example.com").assertIsDisplayed()
    }

    @Test
    fun logOutFromProfileReturnsToLogin() {
        compose.onNodeWithText("Profile").performClick()
        scrollTo("Log out")
        compose.onNodeWithText("Log out").performClick()
        compose.onNodeWithText("Welcome back. Log in to see your groups.").assertIsDisplayed()
    }
}
