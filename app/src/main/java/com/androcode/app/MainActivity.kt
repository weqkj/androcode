package com.androcode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.androcode.app.data.Prefs
import com.androcode.app.ui.AndroTheme
import com.androcode.app.ui.BlurSwitch
import com.androcode.app.ui.ChatScreen
import com.androcode.app.ui.OnboardingScreen
import com.androcode.app.ui.SettingsScreen

private enum class Screen { ONBOARD, CHAT, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        setContent {
            AndroTheme { App() }
        }
    }
}

@Composable
private fun App() {
    var screen by rememberSaveable { mutableStateOf(if (Prefs.onboarded) Screen.CHAT else Screen.ONBOARD) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlurSwitch(screen, { it.ordinal }) { s ->
            when (s) {
                Screen.ONBOARD -> OnboardingScreen(onDone = {
                    Prefs.finishOnboarding()
                    screen = Screen.CHAT
                })
                Screen.CHAT -> ChatScreen(onSettings = { screen = Screen.SETTINGS })
                Screen.SETTINGS -> SettingsScreen(
                    onBack = { screen = Screen.CHAT },
                    onReplayWelcome = {
                        Prefs.resetOnboarding()
                        screen = Screen.ONBOARD
                    },
                )
            }
        }
    }
}
