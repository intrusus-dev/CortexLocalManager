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
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.models.*
import com.cortex.localmanager.ui.components.*
import com.cortex.localmanager.ui.theme.CortexColors
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DetectionsScreen(
    viewModel: DetectionsViewModel,
    onSearchHash: ((String) -> Unit)? = null,
    onAddException: ((String?, String?) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            // Filter bar + export
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
                filteredCount = state.filteredAlerts.size,
                onExportJson = { path -> viewModel.exportAsJson(path, filteredOnly = true) },
                onExportCsv = { path -> viewModel.exportAsCsv(path, filteredOnly = true) },
                exportMessage = state.exportMessage,
                onDismissExport = viewModel::dismissExportMessage
            )

            Spacer(Modifier.height(12.dp))

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
                    onAlertClick = { viewModel.selectAlert(it) },
                    sortColumn = state.sortColumn,
                    sortAscending = state.sortAscending,
                    onSort = viewModel::setSort
                )
            }
        }

        // Detail panel
        val selected = state.selectedAlert
        if (selected != null) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(CortexColors.Divider))
            DetailPanel(
                alert = selected,
                onClose = { viewModel.selectAlert(null) },
                onSearchHash = onSearchHash,
                onAddException = onAddException,
                modifier = Modifier.width(420.dp).fillMaxHeight()
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
    filteredCount: Int,
    onExportJson: (Path) -> Result<Path>,
    onExportCsv: (Path) -> Result<Path>,
    exportMessage: String?,
    onDismissExport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        // Search + count + export
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
                text = "Showing $filteredCount of $totalCount",
                fontSize = 11.sp,
                color = CortexColors.TextMuted
            )

            Spacer(Modifier.width(12.dp))

            ExportButton("JSON") {
                val path = showSaveDialog("json") ?: return@ExportButton
                onExportJson(path)
            }
            Spacer(Modifier.width(4.dp))
            ExportButton("CSV") {
                val path = showSaveDialog("csv") ?: return@ExportButton
                onExportCsv(path)
            }
        }

        // Export message
        if (exportMessage != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\u2713 $exportMessage", fontSize = 11.sp, color = CortexColors.Success)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "\u2715",
                    fontSize = 11.sp,
                    color = CortexColors.TextMuted,
                    modifier = Modifier.clickable { onDismissExport() }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Chips row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Type:", fontSize = 11.sp, color = CortexColors.TextMuted)
            AlertType.entries.forEach { type ->
                FilterChip(label = type.name, isActive = type in activeTypes, onClick = { onToggleType(type) })
            }
            Spacer(Modifier.width(12.dp))
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
            Text("Time:", fontSize = 11.sp, color = CortexColors.TextMuted)
            TimeRange.entries.forEach { range ->
                FilterChip(label = timeRangeLabel(range), isActive = range == timeRange, onClick = { onTimeRangeChange(range) })
            }
        }
    }
}

@Composable
private fun ExportButton(format: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text = "\u2913 $format", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = CortexColors.TextSecondary)
    }
}

private fun showSaveDialog(extension: String): Path? {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val hostname = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "endpoint" }
    val defaultName = "cortex_detections_${hostname}_$timestamp.$extension"

    val dialog = FileDialog(null as java.awt.Frame?, "Export Alerts", FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true

    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file).toPath()
}

@Composable
private fun FilterChip(label: String, isActive: Boolean, activeColor: Color = CortexColors.PaloAltoOrange, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else CortexColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, color = if (isActive) activeColor else CortexColors.TextMuted)
    }
}

// --- Alert Table with Sortable Headers ---

@Composable
private fun AlertTable(
    alerts: List<UnifiedAlert>,
    selectedAlert: UnifiedAlert?,
    onAlertClick: (UnifiedAlert) -> Unit,
    sortColumn: SortColumn,
    sortAscending: Boolean,
    onSort: (SortColumn) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(CortexColors.Surface, RoundedCornerShape(4.dp))
    ) {
        // Sortable header
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SortableHeader("TIME", 140.dp, SortColumn.TIME, sortColumn, sortAscending, onSort)
            SortableHeader("SEV", 70.dp, SortColumn.SEVERITY, sortColumn, sortAscending, onSort)
            SortableHeader("TYPE", 90.dp, SortColumn.TYPE, sortColumn, sortAscending, onSort)
            SortableHeader("COMPONENT", 110.dp, SortColumn.COMPONENT, sortColumn, sortAscending, onSort)
            Box(Modifier.weight(1f)) {
                SortableHeaderText("PROCESS / FILE", SortColumn.PROCESS, sortColumn, sortAscending, onSort)
            }
            Box(Modifier.weight(1.2f)) {
                SortableHeaderText("DESCRIPTION", SortColumn.DESCRIPTION, sortColumn, sortAscending, onSort)
            }
            Box(Modifier.width(110.dp)) { TableHeaderText("HASH") }
            Box(Modifier.width(80.dp)) { TableHeaderText("ACTION") }
        }

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
                    modifier = Modifier.fillMaxWidth().background(bgColor).clickable { onAlertClick(alert) }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(Modifier.width(140.dp)) {
                        Text(alert.timestamp.toString().take(19).replace("T", " "), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CortexColors.TextMuted, maxLines = 1)
                    }
                    Box(Modifier.width(70.dp)) { SeverityBadge(alert.severity) }
                    Box(Modifier.width(90.dp)) {
                        StatusBadge(alert.alertType.name, when (alert.alertType) {
                            AlertType.PREVENTION -> StatusType.ERROR; AlertType.EDR -> StatusType.WARNING; AlertType.TELEMETRY -> StatusType.INFO
                        })
                    }
                    Box(Modifier.width(110.dp)) { Text(alert.componentName ?: "-", fontSize = 11.sp, color = CortexColors.TextSecondary, maxLines = 1) }
                    Box(Modifier.weight(1f)) {
                        Text(alert.processPath?.substringAfterLast("\\") ?: alert.filePath?.substringAfterLast("\\") ?: "-", fontSize = 12.sp, color = CortexColors.TextPrimary, maxLines = 1)
                    }
                    Box(Modifier.weight(1.2f)) { Text(alert.description, fontSize = 11.sp, color = CortexColors.TextSecondary, maxLines = 1) }
                    Box(Modifier.width(110.dp)) {
                        val hash = alert.sha256
                        if (hash != null) HashText(hash = hash, truncate = true) else Text("-", fontSize = 11.sp, color = CortexColors.TextMuted)
                    }
                    Box(Modifier.width(80.dp)) {
                        val action = alert.actionTaken
                        if (action != null) StatusBadge(action, when (action.lowercase()) { "blocked" -> StatusType.ERROR; "reported" -> StatusType.WARNING; else -> StatusType.INFO })
                        else Text("-", fontSize = 11.sp, color = CortexColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortableHeader(text: String, width: Dp, column: SortColumn, currentSort: SortColumn, ascending: Boolean, onSort: (SortColumn) -> Unit) {
    Box(Modifier.width(width)) { SortableHeaderText(text, column, currentSort, ascending, onSort) }
}

@Composable
private fun SortableHeaderText(text: String, column: SortColumn, currentSort: SortColumn, ascending: Boolean, onSort: (SortColumn) -> Unit) {
    val isActive = column == currentSort
    Text(
        text = text + if (isActive) (if (ascending) " \u25B2" else " \u25BC") else "",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = if (isActive) CortexColors.PaloAltoOrange else CortexColors.TextMuted,
        modifier = Modifier.clickable { onSort(column) }
    )
}

@Composable
private fun TableHeaderText(text: String) {
    Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted)
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
    var showRawData by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(CortexColors.Surface).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Alert Detail", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("\u2715", fontSize = 16.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { onClose() })
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(severityColor(alert.severity), RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(12.dp))

        // Badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeverityBadge(alert.severity)
            StatusBadge(alert.alertType.name, when (alert.alertType) {
                AlertType.PREVENTION -> StatusType.ERROR; AlertType.EDR -> StatusType.WARNING; AlertType.TELEMETRY -> StatusType.INFO
            })
            alert.actionTaken?.let {
                StatusBadge(it, when (it.lowercase()) { "blocked" -> StatusType.ERROR; "reported" -> StatusType.WARNING; else -> StatusType.INFO })
            }
        }

        Spacer(Modifier.height(12.dp))

        DetailRow("Timestamp", alert.timestamp.toString().replace("T", " "))
        alert.componentName?.let { DetailRow("Component", it) }
        DetailRow("Description", alert.description)
        DetailRow("Source", alert.source.name)

        Spacer(Modifier.height(16.dp))

        // Hashes
        val sha256 = alert.sha256
        val md5 = alert.md5
        if (sha256 != null || md5 != null) {
            SectionHeader("Hashes")
            sha256?.let { DetailRow("SHA256", it, monospace = true, copyable = true) }
            md5?.let { DetailRow("MD5", it, monospace = true, copyable = true) }
            Spacer(Modifier.height(16.dp))
        }

        // File info
        if (alert.filePath != null || alert.fileSize != null || alert.applicationName != null) {
            SectionHeader("File Info")
            alert.filePath?.let { DetailRow("Path", it, monospace = true, copyable = true) }
            alert.fileSize?.let { DetailRow("Size", formatFileSize(it)) }
            alert.applicationName?.let { DetailRow("Application", it, copyable = true) }
            alert.publisher?.let { DetailRow("Publisher", it, copyable = true) }
            Spacer(Modifier.height(16.dp))
        }

        // Process info
        if (alert.processPath != null || alert.commandLine != null || alert.pid != null || alert.user != null) {
            SectionHeader("Process Info")
            alert.processPath?.let { DetailRow("Process", it, monospace = true, copyable = true) }
            alert.commandLine?.let { DetailRow("Command Line", it, monospace = true, copyable = true) }
            alert.pid?.let { DetailRow("PID", it.toString(), copyable = true) }
            alert.parentPid?.let { DetailRow("Parent PID", it.toString(), copyable = true) }
            alert.user?.let { DetailRow("User", it, copyable = true) }
            alert.userSid?.let { DetailRow("SID", it, monospace = true, copyable = true) }
            Spacer(Modifier.height(16.dp))
        }

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sha256 != null && onSearchHash != null) {
                ActionBtn("Search Hash") { onSearchHash(sha256) }
            }
            if (onAddException != null) {
                ActionBtn("Add Exception") { onAddException(sha256, alert.processPath ?: alert.filePath) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Raw data
        val rawData = alert.rawData
        if (rawData != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showRawData = !showRawData }.padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (showRawData) "\u25BC Raw Data" else "\u25B6 Raw Data",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange
                )
            }
            if (showRawData) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().background(CortexColors.Background, RoundedCornerShape(4.dp)).padding(8.dp)) {
                    Text(rawData, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CortexColors.TextSecondary)
                }
            }
        }
    }
}

// --- Sub-components ---

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DetailRow(label: String, value: String, monospace: Boolean = false, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, fontSize = 10.sp, color = CortexColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 12.sp, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default, color = CortexColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
            if (copyable) {
                Text("\u2398", fontSize = 13.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(start = 6.dp).clickable { clipboard.setText(AnnotatedString(value)) })
            }
        }
    }
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange)
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
    TimeRange.LAST_1H -> "1h"; TimeRange.LAST_6H -> "6h"; TimeRange.LAST_24H -> "24h"
    TimeRange.LAST_7D -> "7d"; TimeRange.LAST_30D -> "30d"; TimeRange.ALL -> "All"
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
