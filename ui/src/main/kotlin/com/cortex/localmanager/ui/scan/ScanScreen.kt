package com.cortex.localmanager.ui.scan

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.theme.CortexColors

@Composable
fun ScanScreen(viewModel: ScanViewModel) {
    val state by viewModel.state.collectAsState()

    // Query status on first display
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // Messages
        state.error?.let {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Error.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A0 $it", fontSize = 11.sp, color = CortexColors.Error, modifier = Modifier.weight(1f))
                    Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        state.message?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("\u2713 $it", fontSize = 12.sp, color = CortexColors.Success)
                Spacer(Modifier.width(8.dp))
                Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
            }
        }

        // Section 1: On-Demand Scan
        ScanSection(
            title = "On-Demand Scan",
            description = "Full scan of the local disk using current content signatures and ML models.",
            status = state.onDemandStatus,
            isRunning = state.onDemandRunning,
            extraInfo = state.lastScanTime?.let { "Last scan: $it" },
            onStart = viewModel::startOnDemandScan,
            onStop = viewModel::stopOnDemandScan
        )

        Spacer(Modifier.height(16.dp))

        // Section 2: File System Scan
        ScanSection(
            title = "File System Scan (Hash Database Refresh)",
            description = "Scans the file system to refresh the local hash database used for threat hunting.",
            status = state.fileSystemScanStatus,
            isRunning = state.fileSystemScanRunning,
            onStart = viewModel::startFileSystemScan,
            onStop = viewModel::stopFileSystemScan
        )

        Spacer(Modifier.height(16.dp))

        // Section 3: DLP Scan
        UnifiedScanSection(
            title = "DLP Scan (Data Loss Prevention)",
            description = "Scans for sensitive data exposure using DLP policies.",
            status = state.dlpScanStatus,
            isRunning = state.dlpScanRunning,
            type = "dlp",
            viewModel = viewModel
        )

        Spacer(Modifier.height(16.dp))

        // Section 4: AMF Scan
        UnifiedScanSection(
            title = "AMF Scan (Anti-Malware Framework)",
            description = "Deep anti-malware scan using the AMF engine.",
            status = state.amfScanStatus,
            isRunning = state.amfScanRunning,
            type = "amf",
            viewModel = viewModel
        )
    }
}

@Composable
private fun ScanSection(
    title: String,
    description: String,
    status: String?,
    isRunning: Boolean,
    extraInfo: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(description, fontSize = 11.sp, color = CortexColors.TextMuted)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRunning) {
                ActionBtn("Stop", CortexColors.Error) { onStop() }
            } else {
                ActionBtn("Start", CortexColors.PaloAltoOrange) { onStart() }
            }

            status?.let {
                Text(it, fontSize = 12.sp, color = CortexColors.TextSecondary, maxLines = 2)
            }
        }

        extraInfo?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, color = CortexColors.TextMuted)
        }

        if (isRunning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                color = CortexColors.PaloAltoOrange,
                trackColor = CortexColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}

@Composable
private fun UnifiedScanSection(
    title: String,
    description: String,
    status: String?,
    isRunning: Boolean,
    type: String,
    viewModel: ScanViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(description, fontSize = 11.sp, color = CortexColors.TextMuted)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isRunning) {
                ActionBtn("Pause", CortexColors.Warning) { viewModel.pauseUnifiedScan(type) }
                ActionBtn("Stop", CortexColors.Error) { viewModel.stopUnifiedScan(type) }
            } else {
                ActionBtn("Start", CortexColors.PaloAltoOrange) { viewModel.startUnifiedScan(type) }
                ActionBtn("Resume", CortexColors.Info) { viewModel.resumeUnifiedScan(type) }
            }

            status?.let {
                Text(it, fontSize = 12.sp, color = CortexColors.TextSecondary, maxLines = 2)
            }
        }

        if (isRunning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                color = CortexColors.PaloAltoOrange,
                trackColor = CortexColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}

@Composable
private fun ActionBtn(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
