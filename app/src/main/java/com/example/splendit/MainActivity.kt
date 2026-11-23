package com.example.splendit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.splendit.notifications.GroupNotifier
import com.example.splendit.ui.auth.AuthGate
import com.example.splendit.ui.navigation.SplitwheelApp
import com.example.splendit.ui.theme.SplenditTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Group to open, from a tapped notification; cleared once the nav graph has opened it. */
    private val openGroup = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the group is already on the back stack.
        if (savedInstanceState == null) openGroup.value = intent.groupId()
        enableEdgeToEdge()
        setContent {
            SplenditTheme {
                AuthGate {
                    val groupId by openGroup.collectAsStateWithLifecycle()
                    SplitwheelApp(openGroupId = groupId, onGroupOpened = { openGroup.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.groupId()?.let { openGroup.value = it }
    }

    private fun Intent.groupId(): String? = getStringExtra(GroupNotifier.EXTRA_GROUP_ID)
}
