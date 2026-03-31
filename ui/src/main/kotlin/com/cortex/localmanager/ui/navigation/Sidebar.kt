package com.cortex.localmanager.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun Sidebar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    isAgentConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(CortexColors.Surface)
    ) {
        // App header
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Cortex Local Manager",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CortexColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Emergency Mode",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = CortexColors.PaloAltoOrange
            )
        }

        HorizontalDivider(color = CortexColors.Divider, thickness = 1.dp)

        Spacer(Modifier.height(8.dp))

        // Navigation items
        Screen.entries.forEach { screen ->
            val isActive = screen == currentScreen
            NavItem(
                screen = screen,
                isActive = isActive,
                onClick = { onScreenSelected(screen) }
            )
        }

        Spacer(Modifier.weight(1f))

        // Agent status at bottom
        HorizontalDivider(color = CortexColors.Divider, thickness = 1.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isAgentConnected) CortexColors.Success
                        else CortexColors.Warning
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isAgentConnected) "Agent Connected" else "Agent Offline",
                fontSize = 12.sp,
                color = if (isAgentConnected) CortexColors.TextSecondary
                else CortexColors.Warning
            )
        }
    }
}

@Composable
private fun NavItem(
    screen: Screen,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isActive) Modifier.background(CortexColors.OrangeDim)
                else Modifier
            )
            .padding(vertical = 10.dp)
    ) {
        // Active indicator bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(
                    if (isActive) CortexColors.PaloAltoOrange
                    else androidx.compose.ui.graphics.Color.Transparent
                )
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = screen.icon,
            fontSize = 16.sp,
            color = if (isActive) CortexColors.PaloAltoOrange else CortexColors.TextMuted
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = screen.label,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) CortexColors.TextPrimary else CortexColors.TextSecondary
            )
        }
    }
}
