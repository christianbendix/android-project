package com.example.splendit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.splendit.ui.navigation.SplitwheelApp
import com.example.splendit.ui.theme.SplenditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplenditTheme {
                SplitwheelApp()
            }
        }
    }
}
