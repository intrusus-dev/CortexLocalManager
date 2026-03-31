package com.cortex.localmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cortex.localmanager.ui.AppContent
import com.cortex.localmanager.ui.ServiceLocator
import com.cortex.localmanager.ui.navigation.Screen
import com.cortex.localmanager.ui.theme.CortexColors
import com.cortex.localmanager.ui.theme.CortexTheme

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cortex Local Manager",
        state = windowState,
    ) {
        val supervisorPassword = remember { mutableStateOf<String?>(null) }
        val serviceLocator = remember { ServiceLocator(supervisorPassword) }

        CortexTheme {
            AppContent(
                supervisorPassword = supervisorPassword,
                onRefresh = { /* Will be wired per-screen */ },
                screenContent = { screen ->
                    // Placeholder screens until Tasks 05-08
                    PlaceholderScreen(screen)
                }
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun PlaceholderScreen(screen: Screen) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "${screen.title}\ncoming soon",
            fontSize = 16.sp,
            color = CortexColors.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
