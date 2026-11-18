package com.example.splendit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.splendit.ui.auth.AuthGate
import com.example.splendit.ui.auth.AuthViewModel
import com.example.splendit.ui.theme.SplenditTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplenditTheme {
                val sessionState by authViewModel.sessionState.collectAsState()
                val form by authViewModel.form.collectAsState()
                AuthGate(
                    sessionState = sessionState,
                    form = form,
                    onChange = authViewModel::onChange,
                    onShowMode = authViewModel::showMode,
                    onSubmit = authViewModel::submit,
                    onLogout = authViewModel::logout
                )
            }
        }
    }
}
