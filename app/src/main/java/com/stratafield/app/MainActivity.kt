package com.stratafield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.stratafield.app.ui.navigation.StrataFieldApp
import com.stratafield.app.ui.theme.StrataFieldTheme
import com.stratafield.app.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by ThemePreferences.isDarkMode(this).collectAsState()
            StrataFieldTheme(darkTheme = isDark) {
                StrataFieldApp()
            }
        }
    }
}