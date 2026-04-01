package com.cortex.localmanager.ui.quarantine

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
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
import com.cortex.localmanager.core.models.QuarantineEntry
import com.cortex.localmanager.ui.components.StatusBadge
import com.cortex.localmanager.ui.components.StatusType
import com.cortex.localmanager.ui.theme.CortexColors
import java.time.Instant as JInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuarantineScreen(viewModel: QuarantineViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state.entries.isEmpty() && !state.isLoading) viewModel.refresh()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                ActionBtn("Refresh") { viewModel.refresh() }
                Spacer(Modifier.width(12.dp))
                Text("${state.entries.size} quarantined file${if (state.entries.size != 1) "s" else ""}",
                    fontSize = 12.sp, color = CortexColors.TextMuted)
            }

            // Messages
            state.error?.let {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().background(CortexColors.Error.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\u26A0 $it", fontSize = 11.sp, color = CortexColors.Error, modifier = Modifier.weight(1f))
                        Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
                    }
                }
            }
            state.message?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("\u2713 $it", fontSize = 11.sp, color = CortexColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
                }
            }

            if (state.isLoading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(color = CortexColors.PaloAltoOrange, trackColor = CortexColors.SurfaceVariant, modifier = Modifier.fillMaxWidth().height(3.dp))
            }

            Spacer(Modifier.height(8.dp))

            if (state.entries.isEmpty() && !state.isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(CortexColors.Surface, RoundedCornerShape(4.dp))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No quarantined files", fontSize = 14.sp, color = CortexColors.TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text("Quarantined files will appear here when the agent blocks and quarantines a threat.", fontSize = 12.sp, color = CortexColors.TextMuted.copy(alpha = 0.6f))
                    }
                }
                return@Box
            }

            // Table
            Column(modifier = Modifier.fillMaxSize().background(CortexColors.Surface, RoundedCornerShape(4.dp))) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Box(Modifier.weight(1f)) { HeaderText("FILE NAME") }
                    Box(Modifier.width(130.dp)) { HeaderText("SHA256") }
                    Box(Modifier.width(70.dp)) { HeaderText("SIZE") }
                    Box(Modifier.width(140.dp)) { HeaderText("QUARANTINED") }
                    Box(Modifier.width(80.dp)) { HeaderText("STATUS") }
                    Box(Modifier.width(140.dp)) { HeaderText("ACTIONS") }
                }

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    state.entries.forEachIndexed { index, entry ->
                        val isSelected = entry == state.selectedEntry
                        val bg = when {
                            isSelected -> CortexColors.OrangeDim
                            index % 2 == 0 -> CortexColors.Surface
                            else -> CortexColors.Background
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(bg)
                                .clickable { viewModel.selectEntry(if (isSelected) null else entry) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // File name (not full path — detail panel shows full info)
                            Box(Modifier.weight(1f)) {
                                Text(
                                    entry.filePath.substringAfterLast("\\"),
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    color = CortexColors.TextPrimary, maxLines = 1
                                )
                            }

                            // Hash truncated
                            Box(Modifier.width(130.dp)) {
                                Text(
                                    entry.sha256.take(8) + "..." + entry.sha256.takeLast(8),
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = CortexColors.TextSecondary, maxLines = 1
                                )
                            }

                            Box(Modifier.width(70.dp)) {
                                Text(formatSize(entry.size), fontSize = 11.sp, color = CortexColors.TextSecondary)
                            }

                            Box(Modifier.width(140.dp)) {
                                Text(formatLocalTime(entry.timestamp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextMuted)
                            }

                            Box(Modifier.width(80.dp)) {
                                StatusBadge(
                                    entry.status.replace("Quarantine", ""),
                                    when {
                                        entry.status.contains("Done", ignoreCase = true) -> StatusType.QUARANTINED
                                        entry.status.contains("Fail", ignoreCase = true) -> StatusType.ERROR
                                        else -> StatusType.INFO
                                    }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.width(140.dp)) {
                                SmallBtn("Restore", CortexColors.Success) { viewModel.requestRestore(entry) }
                                SmallBtn("Delete", CortexColors.Error) { viewModel.requestDelete(entry) }
                            }
                        }
                    }
                }
            }
        }

        // Detail panel overlay from right
        val selected = state.selectedEntry
        if (selected != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { viewModel.selectEntry(null) })
            QuarantineDetailPanel(
                entry = selected,
                onClose = { viewModel.selectEntry(null) },
                onRestore = { viewModel.requestRestore(selected) },
                onDelete = { viewModel.requestDelete(selected) },
                modifier = Modifier.width(440.dp).fillMaxHeight().align(Alignment.CenterEnd)
            )
        }

        // Confirmation dialog
        val confirm = state.confirmAction
        if (confirm != null) {
            ConfirmDialog(confirm, viewModel)
        }
    }
}

@Composable
private fun QuarantineDetailPanel(
    entry: QuarantineEntry,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier.background(CortexColors.Surface).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Quarantine Detail", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("\u2715", fontSize = 16.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { onClose() })
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(CortexColors.ActionQuarantined, RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(12.dp))

        // Status badge
        StatusBadge(entry.status.replace("Quarantine", ""), when {
            entry.status.contains("Done", ignoreCase = true) -> StatusType.QUARANTINED
            else -> StatusType.ERROR
        })

        Spacer(Modifier.height(16.dp))

        // File info
        SectionHeader("File Information")
        DetailRow("File Name", entry.filePath.substringAfterLast("\\"))
        DetailRow("Original Path", entry.filePath, mono = true, copyable = true)
        DetailRow("Size", formatSize(entry.size))
        DetailRow("Application", entry.applicationName.ifBlank { "-" })

        Spacer(Modifier.height(16.dp))

        // Hashes
        SectionHeader("Hashes")
        DetailRow("SHA256", entry.sha256, mono = true, copyable = true)

        Spacer(Modifier.height(16.dp))

        // Quarantine info
        SectionHeader("Quarantine Details")
        DetailRow("Quarantine ID", entry.quarantineId, mono = true, copyable = true)
        DetailRow("Quarantined At", formatLocalTime(entry.timestamp))
        DetailRow("Backup Path", entry.backupPath, mono = true, copyable = true)
        DetailRow("Action ID", entry.actionId, mono = true, copyable = true)
        DetailRow("Status", entry.status)

        Spacer(Modifier.height(20.dp))

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn("Restore File", CortexColors.Success) { onRestore() }
            SmallBtn("Delete Permanently", CortexColors.Error) { onDelete() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                    .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
                    .clickable { clipboard.setText(AnnotatedString(entry.sha256)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Copy SHA256", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false, copyable: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, fontSize = 10.sp, color = CortexColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 12.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, color = CortexColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
            if (copyable) {
                Text("\u2398", fontSize = 13.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(start = 6.dp).clickable { clipboard.setText(AnnotatedString(value)) })
            }
        }
    }
}

@Composable
private fun ConfirmDialog(action: ConfirmAction, viewModel: QuarantineViewModel) {
    var customPath by remember { mutableStateOf("") }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { viewModel.cancelAction() }
    ) {
        Column(
            modifier = Modifier.width(480.dp)
                .background(CortexColors.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, CortexColors.Border, RoundedCornerShape(8.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            val isRestore = action.type == ActionType.RESTORE
            Text(
                if (isRestore) "Restore from Quarantine" else "Delete from Quarantine",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isRestore) "This will restore the file to its original location. The file may be malicious."
                else "This will permanently delete the quarantined file. This cannot be undone.",
                fontSize = 12.sp, color = CortexColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            Text("File:", fontSize = 11.sp, color = CortexColors.TextMuted)
            Text(action.entry.filePath, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("SHA256:", fontSize = 11.sp, color = CortexColors.TextMuted)
            Text(action.entry.sha256, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextPrimary)

            if (isRestore) {
                Spacer(Modifier.height(12.dp))
                Text("Custom restore path (optional):", fontSize = 11.sp, color = CortexColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (customPath.isEmpty()) Text("Leave empty to restore to original location", fontSize = 11.sp, color = CortexColors.TextMuted)
                    BasicTextField(
                        value = customPath, onValueChange = { customPath = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextPrimary),
                        cursorBrush = SolidColor(CortexColors.PaloAltoOrange), modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                ActionBtn("Cancel") { viewModel.cancelAction() }
                if (isRestore) {
                    ConfirmBtn("Confirm Restore", CortexColors.Success) { viewModel.confirmRestore(customPath.ifBlank { null }) }
                } else {
                    ConfirmBtn("Confirm Delete", CortexColors.Error) { viewModel.confirmDelete() }
                }
            }
        }
    }
}

// --- Helpers ---

@Composable
private fun HeaderText(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted)
}

@Composable
private fun ActionBtn(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange) }
}

@Composable
private fun SmallBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp)
    ) { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color) }
}

@Composable
private fun ConfirmBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color) }
}

private val localTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun formatLocalTime(timestamp: kotlinx.datetime.Instant): String {
    return try {
        val javaInstant = JInstant.ofEpochSecond(timestamp.epochSeconds)
        localTimeFmt.format(javaInstant.atZone(ZoneId.systemDefault()))
    } catch (_: Exception) { timestamp.toString().take(19) }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
