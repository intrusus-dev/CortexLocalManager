package com.cortex.localmanager.ui.detections

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.models.*
import com.cortex.localmanager.ui.components.*
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun DetectionsScreen(
    viewModel: DetectionsViewModel,
    onSearchHash: ((String) -> Unit)? = null,
    onAddException: ((String?, String?) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        // Main content (table + filters)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Filter bar
            FilterBar(
                searchQuery = state.searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                activeSeverities = state.activeSeverities,
                onToggleSeverity = viewModel::toggleSeverityFilter,
                activeTypes = state.activeTypes,
                onToggleType = viewModel::toggleTypeFilter,
                timeRange = state.timeRange,
                onTimeRangeChange = viewModel::setTimeRange,
                totalCount = state.allAlerts.size,
                filteredCount = state.filteredAlerts.size
            )

            Spacer(Modifier.height(12.dp))

            // Alert table
            if (state.filteredAlerts.isEmpty() && !state.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = if (state.allAlerts.isEmpty()) "No alerts found"
                        else "No alerts match current filters",
                        fontSize = 14.sp,
                        color = CortexColors.TextMuted
                    )
                }
            } else {
                AlertTable(
                    alerts = state.filteredAlerts,
                    selectedAlert = state.selectedAlert,
                    onAlertClick = { viewModel.selectAlert(it) }
                )
            }
        }

        // Detail panel (slide-out)
        val selected = state.selectedAlert
        if (selected != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(CortexColors.Divider)
            )
            DetailPanel(
                alert = selected,
                onClose = { viewModel.selectAlert(null) },
                onSearchHash = onSearchHash,
                onAddException = onAddException,
                modifier = Modifier.width(400.dp).fillMaxHeight()
            )
        }
    }
}

// --- Filter Bar ---

@Composable
private fun FilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    activeSeverities: Set<Severity>,
    onToggleSeverity: (Severity) -> Unit,
    activeTypes: Set<AlertType>,
    onToggleType: (AlertType) -> Unit,
    timeRange: TimeRange,
    onTimeRangeChange: (TimeRange) -> Unit,
    totalCount: Int,
    filteredCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        // Search + count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search alerts (process, hash, description, user)...",
                        fontSize = 12.sp,
                        color = CortexColors.TextMuted
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp, color = CortexColors.TextPrimary),
                    cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = "Showing $filteredCount of $totalCount alerts",
                fontSize = 11.sp,
                color = CortexColors.TextMuted
            )
        }

        Spacer(Modifier.height(8.dp))

        // Chips row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Type chips
            Text("Type:", fontSize = 11.sp, color = CortexColors.TextMuted)
            AlertType.entries.forEach { type ->
                FilterChip(
                    label = type.name,
                    isActive = type in activeTypes,
                    onClick = { onToggleType(type) }
                )
            }

            Spacer(Modifier.width(12.dp))

            // Severity chips
            Text("Severity:", fontSize = 11.sp, color = CortexColors.TextMuted)
            Severity.entries.forEach { severity ->
                FilterChip(
                    label = severity.name,
                    isActive = severity in activeSeverities,
                    activeColor = severityColor(severity),
                    onClick = { onToggleSeverity(severity) }
                )
            }

            Spacer(Modifier.width(12.dp))

            // Time range
            Text("Time:", fontSize = 11.sp, color = CortexColors.TextMuted)
            TimeRange.entries.forEach { range ->
                FilterChip(
                    label = timeRangeLabel(range),
                    isActive = range == timeRange,
                    onClick = { onTimeRangeChange(range) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isActive: Boolean,
    activeColor: Color = CortexColors.PaloAltoOrange,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isActive) activeColor.copy(alpha = 0.2f)
                else CortexColors.SurfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) activeColor else CortexColors.TextMuted
        )
    }
}

// --- Alert Table ---

@Composable
private fun AlertTable(
    alerts: List<UnifiedAlert>,
    selectedAlert: UnifiedAlert?,
    onAlertClick: (UnifiedAlert) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CortexColors.SurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TableHeader("TIME", 140.dp)
            TableHeader("SEV", 70.dp)
            TableHeader("TYPE", 90.dp)
            TableHeader("COMPONENT", 110.dp)
            Box(Modifier.weight(1f)) { TableHeaderText("PROCESS / FILE") }
            Box(Modifier.weight(1.2f)) { TableHeaderText("DESCRIPTION") }
            TableHeader("HASH", 110.dp)
            TableHeader("ACTION", 80.dp)
        }

        // Rows
        LazyColumn {
            itemsIndexed(alerts) { index, alert ->
                val isSelected = alert == selectedAlert
                val bgColor = when {
                    isSelected -> CortexColors.OrangeDim
                    index % 2 == 0 -> CortexColors.Surface
                    else -> CortexColors.Background
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .clickable { onAlertClick(alert) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Time
                    Box(Modifier.width(140.dp)) {
                        Text(
                            text = alert.timestamp.toString().take(19).replace("T", " "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CortexColors.TextMuted,
                            maxLines = 1
                        )
                    }

                    // Severity
                    Box(Modifier.width(70.dp)) { SeverityBadge(alert.severity) }

                    // Type
                    Box(Modifier.width(90.dp)) {
                        StatusBadge(
                            text = alert.alertType.name,
                            type = when (alert.alertType) {
                                AlertType.PREVENTION -> StatusType.ERROR
                                AlertType.EDR -> StatusType.WARNING
                                AlertType.TELEMETRY -> StatusType.INFO
                            }
                        )
                    }

                    // Component
                    Box(Modifier.width(110.dp)) {
                        Text(
                            text = alert.componentName ?: "-",
                            fontSize = 11.sp,
                            color = CortexColors.TextSecondary,
                            maxLines = 1
                        )
                    }

                    // Process / File
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = alert.processPath?.substringAfterLast("\\")
                                ?: alert.filePath?.substringAfterLast("\\")
                                ?: "-",
                            fontSize = 12.sp,
                            color = CortexColors.TextPrimary,
                            maxLines = 1
                        )
                    }

                    // Description
                    Box(Modifier.weight(1.2f)) {
                        Text(
                            text = alert.description,
                            fontSize = 11.sp,
                            color = CortexColors.TextSecondary,
                            maxLines = 1
                        )
                    }

                    // Hash
                    Box(Modifier.width(110.dp)) {
                        val hash = alert.sha256
                        if (hash != null) {
                            HashText(hash = hash, truncate = true)
                        } else {
                            Text("-", fontSize = 11.sp, color = CortexColors.TextMuted)
                        }
                    }

                    // Action
                    Box(Modifier.width(80.dp)) {
                        val action = alert.actionTaken
                        if (action != null) {
                            StatusBadge(
                                text = action,
                                type = when (action.lowercase()) {
                                    "blocked" -> StatusType.ERROR
                                    "reported" -> StatusType.WARNING
                                    else -> StatusType.INFO
                                }
                            )
                        } else {
                            Text("-", fontSize = 11.sp, color = CortexColors.TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width)) { TableHeaderText(text) }
}

@Composable
private fun TableHeaderText(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = CortexColors.TextMuted
    )
}

// --- Detail Panel ---

@Composable
private fun DetailPanel(
    alert: UnifiedAlert,
    onClose: () -> Unit,
    onSearchHash: ((String) -> Unit)?,
    onAddException: ((String?, String?) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showRawData by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(CortexColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Alert Detail",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = CortexColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "\u2715",
                fontSize = 16.sp,
                color = CortexColors.TextMuted,
                modifier = Modifier.clickable { onClose() }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Severity bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(severityColor(alert.severity), RoundedCornerShape(2.dp))
        )

        Spacer(Modifier.height(12.dp))

        // Badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityBadge(alert.severity)
            StatusBadge(
                text = alert.alertType.name,
                type = when (alert.alertType) {
                    AlertType.PREVENTION -> StatusType.ERROR
                    AlertType.EDR -> StatusType.WARNING
                    AlertType.TELEMETRY -> StatusType.INFO
                }
            )
            alert.actionTaken?.let {
                StatusBadge(
                    text = it,
                    type = when (it.lowercase()) {
                        "blocked" -> StatusType.ERROR
                        "reported" -> StatusType.WARNING
                        else -> StatusType.INFO
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Timestamp
        DetailRow("Timestamp", alert.timestamp.toString().replace("T", " "))

        // Component
        alert.componentName?.let { DetailRow("Component", it) }

        // Description
        DetailRow("Description", alert.description)

        // Source
        DetailRow("Source", alert.source.name)

        Spacer(Modifier.height(16.dp))

        // Hashes section
        val sha256 = alert.sha256
        val md5 = alert.md5
        if (sha256 != null || md5 != null) {
            SectionHeader("Hashes")
            sha256?.let {
                DetailRow("SHA256", it, monospace = true, copyable = true)
            }
            md5?.let {
                DetailRow("MD5", it, monospace = true, copyable = true)
            }
            Spacer(Modifier.height(16.dp))
        }

        // File info
        val filePath = alert.filePath
        if (filePath != null) {
            SectionHeader("File Info")
            DetailRow("Path", filePath, monospace = true)
            Spacer(Modifier.height(16.dp))
        }

        // Process info
        val processPath = alert.processPath
        val commandLine = alert.commandLine
        val user = alert.user
        if (processPath != null || commandLine != null || user != null) {
            SectionHeader("Process Info")
            processPath?.let { DetailRow("Process", it, monospace = true) }
            commandLine?.let { DetailRow("Command Line", it, monospace = true) }
            user?.let { DetailRow("User", it) }
            Spacer(Modifier.height(16.dp))
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sha256 != null && onSearchHash != null) {
                ActionBtn("Search Hash") { onSearchHash(sha256) }
            }
            if (onAddException != null) {
                ActionBtn("Add Exception") {
                    onAddException(sha256, processPath ?: filePath)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Raw data toggle
        val rawData = alert.rawData
        if (rawData != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { showRawData = !showRawData }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (showRawData) "\u25BC Raw Data" else "\u25B6 Raw Data",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CortexColors.PaloAltoOrange
                )
            }

            if (showRawData) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CortexColors.Background, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = rawData,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CortexColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = CortexColors.TextMuted,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    copyable: Boolean = false
) {
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = CortexColors.TextMuted
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = CortexColors.TextPrimary,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (copyable) {
                Text(
                    text = "\u2398",
                    fontSize = 13.sp,
                    color = CortexColors.TextMuted,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable { clipboard.setText(AnnotatedString(value)) }
                )
            }
        }
    }
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = CortexColors.PaloAltoOrange
        )
    }
}

// --- Helpers ---

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> CortexColors.SeverityCritical
    Severity.HIGH -> CortexColors.SeverityHigh
    Severity.MEDIUM -> CortexColors.SeverityMedium
    Severity.LOW -> CortexColors.SeverityLow
    Severity.INFO -> CortexColors.SeverityInfo
}

private fun timeRangeLabel(range: TimeRange): String = when (range) {
    TimeRange.LAST_1H -> "1h"
    TimeRange.LAST_6H -> "6h"
    TimeRange.LAST_24H -> "24h"
    TimeRange.LAST_7D -> "7d"
    TimeRange.LAST_30D -> "30d"
    TimeRange.ALL -> "All"
}
