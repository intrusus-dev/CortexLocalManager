package com.cortex.localmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.components.LoadingIndicator
import com.cortex.localmanager.ui.navigation.Screen
import com.cortex.localmanager.ui.navigation.Sidebar
import com.cortex.localmanager.ui.theme.CortexColors
import kotlinx.coroutines.launch

@Composable
fun AppContent(
    supervisorPassword: MutableState<String?>,
    isOffline: Boolean = false,
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onRefresh: () -> Unit,
    onValidatePassword: suspend (String) -> Boolean,
    alertBadgeCount: Int = 0,
    screenContent: @Composable (Screen) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().background(CortexColors.Background)) {
        Sidebar(
            currentScreen = currentScreen,
            onScreenSelected = onScreenSelected,
            isAgentConnected = !isOffline,
            alertBadgeCount = alertBadgeCount
        )

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(CortexColors.Divider)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                currentScreen = currentScreen,
                supervisorPassword = supervisorPassword,
                onRefresh = onRefresh,
                onValidatePassword = onValidatePassword
            )

            HorizontalDivider(color = CortexColors.Divider, thickness = 1.dp)

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
    onRefresh: () -> Unit,
    onValidatePassword: suspend (String) -> Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CortexColors.Surface)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = currentScreen.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = CortexColors.TextPrimary
        )

        Spacer(Modifier.weight(1f))

        PasswordSection(supervisorPassword, onValidatePassword)

        Spacer(Modifier.width(16.dp))

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
private fun PasswordSection(
    supervisorPassword: MutableState<String?>,
    onValidatePassword: suspend (String) -> Boolean
) {
    if (supervisorPassword.value != null) {
        // Unlocked state
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(CortexColors.Success.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = "\uD83D\uDD12", fontSize = 14.sp)
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
        // Password input with validation
        var input by remember { mutableStateOf("") }
        var isValidating by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .background(
                            if (errorMessage != null) CortexColors.Error.copy(alpha = 0.15f)
                            else CortexColors.SurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
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
                        onValueChange = {
                            input = it
                            errorMessage = null
                        },
                        singleLine = true,
                        enabled = !isValidating,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            color = CortexColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 10.sp,
                        color = CortexColors.Error,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (isValidating) {
                LoadingIndicator("Verifying...")
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(CortexColors.PaloAltoOrange, RoundedCornerShape(4.dp))
                        .clickable {
                            if (input.isNotEmpty()) {
                                isValidating = true
                                errorMessage = null
                                scope.launch {
                                    val valid = onValidatePassword(input)
                                    if (valid) {
                                        supervisorPassword.value = input
                                        input = ""
                                    } else {
                                        errorMessage = "Invalid password"
                                    }
                                    isValidating = false
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Unlock",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
