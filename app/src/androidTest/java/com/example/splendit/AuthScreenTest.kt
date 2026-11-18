package com.example.splendit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule(order = 0)
    val auth = FakeAuthRule(initial = null)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun signedOut_showsLogin_andValidatesBeforeSubmitting() {
        compose.onNodeWithText("Welcome back. Log in to see your groups.").assertIsDisplayed()

        compose.onNode(hasText("Log in") and hasClickAction()).performClick()

        compose.onNodeWithText("Enter a valid email address").assertIsDisplayed()
        compose.onNodeWithText("Required").assertIsDisplayed()
    }

    @Test
    fun createAccountLink_opensSignUp_andBackReturnsToLogin() {
        compose.onNodeWithText("Create account").performClick()
        compose.onNodeWithText("Split bills with your friends.").assertIsDisplayed()
        compose.onNodeWithText("Confirm password").assertIsDisplayed()

        pressBack()

        compose.onNodeWithText("Welcome back. Log in to see your groups.").assertIsDisplayed()
    }
}
