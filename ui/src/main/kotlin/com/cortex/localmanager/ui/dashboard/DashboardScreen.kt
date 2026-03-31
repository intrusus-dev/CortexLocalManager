package com.cortex.localmanager.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.models.ProtectionFeature
import com.cortex.localmanager.core.models.Severity
import com.cortex.localmanager.core.models.UnifiedAlert
import com.cortex.localmanager.ui.components.*
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAlertClick: ((UnifiedAlert) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Row 1 — Status Cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Agent Info card
            DashboardCard(
                title = "Agent Info",
                error = state.agentInfoError,
                isLoading = state.isLoading && state.agentInfo == null,
                modifier = Modifier.weight(1f)
            ) {
                val info = state.agentInfo
                if (info != null) {
                    InfoRow("Content Version", info.contentVersion)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Content Type", info.contentType.toString())
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Content Build", info.contentBuild.toString())
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Event Log", if (info.eventLog == 1) "Enabled" else "Disabled")
                }
            }

            // Protection Status card
            DashboardCard(
                title = "Protection Status",
                error = state.protectionError,
                isLoading = state.isLoading && state.protectionFeatures.isEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                if (state.protectionFeatures.isNotEmpty()) {
                    state.protectionFeatures.forEach { feature ->
                        ProtectionRow(feature)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Last Check-in card
            DashboardCard(
                title = "Last Check-in",
                error = state.lastCheckinError,
                isLoading = state.isLoading && state.lastCheckinDisplay == null,
                modifier = Modifier.weight(1f)
            ) {
                val timestamp = state.lastCheckinTimestamp
                val display = state.lastCheckinDisplay
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = CortexColors.TextPrimary
                    )
                } else if (display != null) {
                    Text(
                        text = display,
                        fontSize = 12.sp,
                        color = CortexColors.TextSecondary
                    )
                } else if (state.lastCheckinError == null && !state.isLoading) {
                    Text(
                        text = "No check-in data",
                        fontSize = 13.sp,
                        color = CortexColors.TextMuted
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (state.isOffline) {
                    StatusBadge("OFFLINE", StatusType.WARNING)
                } else {
                    StatusBadge("CONNECTED", StatusType.SUCCESS)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Row 2 — Quick Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                text = "\u21BB  Refresh All",
                onClick = { viewModel.refresh() }
            )
            ActionButton(
                text = if (state.checkinInProgress) "Checking in..." else "\u2197  Force Check-in",
                onClick = { if (!state.checkinInProgress) viewModel.forceCheckin() },
                enabled = !state.checkinInProgress
            )
        }

        Spacer(Modifier.height(24.dp))

        // Row 3 — Recent Alerts Feed
        Text(
            text = "Recent Alerts",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = CortexColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${state.recentAlerts.size} alerts",
            fontSize = 12.sp,
            color = CortexColors.TextMuted
        )

        Spacer(Modifier.height(12.dp))

        if (state.recentAlerts.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = "No recent alerts",
                    fontSize = 14.sp,
                    color = CortexColors.TextMuted
                )
            }
        } else {
            // Use a fixed-height container for the alert list within the scrollable column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            ) {
                state.recentAlerts.forEachIndexed { index, alert ->
                    AlertRow(
                        alert = alert,
                        onClick = { onAlertClick?.invoke(alert) },
                        bgColor = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background
                    )
                }
            }
        }
    }
}

// --- Sub-components ---

@Composable
private fun DashboardCard(
    title: String,
    error: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = CortexColors.TextMuted
        )
        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> LoadingIndicator("Fetching...")
            error != null -> ErrorBanner(error)
            else -> content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = CortexColors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = CortexColors.TextPrimary
        )
    }
}

@Composable
private fun ProtectionRow(feature: ProtectionFeature) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature.name,
            fontSize = 12.sp,
            color = CortexColors.TextSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = feature.mode,
                fontSize = 11.sp,
                color = CortexColors.TextMuted
            )
            StatusBadge(
                text = if (feature.enabled) "Enabled" else "Disabled",
                type = if (feature.enabled) StatusType.SUCCESS else StatusType.ERROR
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(
                if (enabled) CortexColors.SurfaceVariant else CortexColors.Surface,
                RoundedCornerShape(4.dp)
            )
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) CortexColors.TextPrimary else CortexColors.TextMuted
        )
    }
}

@Composable
private fun AlertRow(
    alert: UnifiedAlert,
    onClick: () -> Unit,
    bgColor: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Timestamp
        Text(
            text = alert.timestamp.toString().take(19).replace("T", " "),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = CortexColors.TextMuted,
            modifier = Modifier.width(140.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Severity badge
        SeverityBadge(alert.severity)

        Spacer(Modifier.width(8.dp))

        // Alert type badge
        StatusBadge(
            text = alert.alertType.name,
            type = when (alert.alertType) {
                com.cortex.localmanager.core.models.AlertType.PREVENTION -> StatusType.ERROR
                com.cortex.localmanager.core.models.AlertType.EDR -> StatusType.WARNING
                com.cortex.localmanager.core.models.AlertType.TELEMETRY -> StatusType.INFO
            }
        )

        Spacer(Modifier.width(12.dp))

        // Process/file name
        Text(
            text = alert.processPath?.substringAfterLast("\\")
                ?: alert.filePath?.substringAfterLast("\\")
                ?: "-",
            fontSize = 12.sp,
            color = CortexColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(8.dp))

        // Description
        Text(
            text = alert.description,
            fontSize = 12.sp,
            color = CortexColors.TextSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1.5f)
        )
    }
}
