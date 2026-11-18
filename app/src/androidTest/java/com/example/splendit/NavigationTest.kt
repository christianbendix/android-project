package com.example.splendit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeListsGroupsAndOpensChat() {
        compose.onNodeWithText("Roskilde 2026").assertIsDisplayed().performClick()
        compose.onNodeWithText("Tent is packed. Who is on groceries?").assertIsDisplayed()
        compose.onNodeWithText("You owe Mads 210 kr").assertIsDisplayed()
    }

    @Test
    fun bottomBarSwitchesToProfile() {
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Peter Hansen").assertIsDisplayed()
    }
}
