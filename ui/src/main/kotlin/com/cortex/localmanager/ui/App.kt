package com.cortex.localmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.navigation.Screen
import com.cortex.localmanager.ui.navigation.Sidebar
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun AppContent(
    supervisorPassword: MutableState<String?>,
    onRefresh: () -> Unit,
    screenContent: @Composable (Screen) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    Row(modifier = Modifier.fillMaxSize().background(CortexColors.Background)) {
        // Sidebar
        Sidebar(
            currentScreen = currentScreen,
            onScreenSelected = { currentScreen = it },
            isAgentConnected = true // Will be wired to real status later
        )

        // Vertical divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(CortexColors.Divider)
        )

        // Main content area
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(
                currentScreen = currentScreen,
                supervisorPassword = supervisorPassword,
                onRefresh = onRefresh
            )

            HorizontalDivider(color = CortexColors.Divider, thickness = 1.dp)

            // Screen content
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                screenContent(currentScreen)
            }
        }
    }
}

@Composable
private fun TopBar(
    currentScreen: Screen,
    supervisorPassword: MutableState<String?>,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CortexColors.Surface)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Screen title
        Text(
            text = currentScreen.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = CortexColors.TextPrimary
        )

        Spacer(Modifier.weight(1f))

        // Password field or lock indicator
        PasswordSection(supervisorPassword)

        Spacer(Modifier.width(16.dp))

        // Refresh button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                .clickable { onRefresh() }
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = "\u21BB  Refresh",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CortexColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PasswordSection(supervisorPassword: MutableState<String?>) {
    if (supervisorPassword.value != null) {
        // Locked state — password entered
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(CortexColors.Success.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "\uD83D\uDD12",
                fontSize = 14.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Unlocked",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CortexColors.Success
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "\u2715",
                fontSize = 12.sp,
                color = CortexColors.TextMuted,
                modifier = Modifier.clickable { supervisorPassword.value = null }
            )
        }
    } else {
        // Password input
        var input by remember { mutableStateOf("") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = "Supervisor password...",
                        fontSize = 12.sp,
                        color = CortexColors.TextMuted
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(
                        fontSize = 12.sp,
                        color = CortexColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(CortexColors.PaloAltoOrange, RoundedCornerShape(4.dp))
                    .clickable {
                        if (input.isNotEmpty()) {
                            supervisorPassword.value = input
                            input = ""
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Unlock",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
