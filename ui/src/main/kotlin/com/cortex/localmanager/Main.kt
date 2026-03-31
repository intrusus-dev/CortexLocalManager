package com.cortex.localmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cortex.localmanager.ui.AppContent
import com.cortex.localmanager.ui.ServiceLocator
import com.cortex.localmanager.ui.dashboard.DashboardScreen
import com.cortex.localmanager.ui.dashboard.DashboardViewModel
import com.cortex.localmanager.ui.detections.DetectionsScreen
import com.cortex.localmanager.ui.detections.DetectionsViewModel
import com.cortex.localmanager.ui.navigation.Screen
import com.cortex.localmanager.ui.theme.CortexColors
import com.cortex.localmanager.ui.theme.CortexTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cortex Local Manager",
        state = windowState,
    ) {
        val supervisorPassword = remember { mutableStateOf<String?>(null) }
        val serviceLocator = remember {
            ServiceLocator(supervisorPassword).also { it.initialize() }
        }
        val uiScope = remember { CoroutineScope(Dispatchers.Default) }
        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

        val dashboardViewModel = remember {
            DashboardViewModel(
                cytoolCommands = serviceLocator.cytoolCommands,
                logRepository = serviceLocator.logRepository,
                scope = uiScope
            )
        }

        val detectionsViewModel = remember {
            DetectionsViewModel(
                logRepository = serviceLocator.logRepository,
                scope = uiScope
            )
        }

        val dashboardState by dashboardViewModel.state.collectAsState()

        CortexTheme {
            AppContent(
                supervisorPassword = supervisorPassword,
                isOffline = dashboardState.isOffline,
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
                onRefresh = {
                    when (currentScreen) {
                        Screen.DASHBOARD -> dashboardViewModel.refresh()
                        Screen.DETECTIONS -> detectionsViewModel.refresh()
                        else -> {}
                    }
                    // Also refresh security events if password available
                    if (supervisorPassword.value != null) {
                        serviceLocator.loadSecurityEvents()
                    }
                },
                onValidatePassword = { password ->
                    val valid = serviceLocator.cytoolCommands.validatePassword(password)
                    if (valid) {
                        // Password works — load rich security events
                        serviceLocator.loadSecurityEvents()
                    }
                    valid
                },
                screenContent = { screen ->
                    when (screen) {
                        Screen.DASHBOARD -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            onAlertClick = { alert ->
                                detectionsViewModel.selectAlert(alert)
                                currentScreen = Screen.DETECTIONS
                            }
                        )
                        Screen.DETECTIONS -> DetectionsScreen(
                            viewModel = detectionsViewModel,
                            onSearchHash = { hash ->
                                // Will navigate to Hunting in Task 07
                            },
                            onAddException = { hash, path ->
                                // Will navigate to Exceptions in Task 08
                            }
                        )
                        else -> PlaceholderScreen(screen)
                    }
                }
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(screen: Screen) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "${screen.title}\ncoming soon",
            fontSize = 16.sp,
            color = CortexColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
