package com.cortex.localmanager.ui.forensics

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.ui.components.StatusBadge
import com.cortex.localmanager.ui.components.StatusType
import com.cortex.localmanager.ui.theme.CortexColors
import java.awt.FileDialog
import java.io.File

@Composable
fun ForensicsScreen(viewModel: ForensicsViewModel) {
    val state by viewModel.state.collectAsState()
    val progress = state.progress

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Error
        state.error?.let {
            Box(Modifier.fillMaxWidth().background(CortexColors.Error.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u26A0 $it", fontSize = 11.sp, color = CortexColors.Error, modifier = Modifier.weight(1f))
                    Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissError() })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Header card
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
                .padding(20.dp)
        ) {
            Text("Forensic Artifact Collection", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "One-click collection of all forensic artifacts from this endpoint. " +
                    "Collects cytool persist databases (security events, IoC lists, verdicts, quarantine), " +
                    "Windows artifacts (prefetch, scheduled tasks, services, DNS cache, Event Log), " +
                    "and agent logs. Everything is bundled into a timestamped zip file.",
                fontSize = 12.sp, color = CortexColors.TextMuted, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            if (!progress.isRunning && progress.outputPath == null) {
                // Start button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(CortexColors.PaloAltoOrange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, CortexColors.PaloAltoOrange, RoundedCornerShape(6.dp))
                        .clickable {
                            val dialog = FileDialog(null as java.awt.Frame?, "Select Output Directory", FileDialog.SAVE)
                            dialog.file = "forensics_collection"
                            dialog.isVisible = true
                            val dir = dialog.directory
                            if (dir != null) {
                                viewModel.startCollection(File(dir).toPath())
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Collect Forensic Artifacts", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.PaloAltoOrange)
                }
            }
        }

        // Progress section
        if (progress.isRunning || progress.outputPath != null) {
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Collection Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    if (progress.isRunning) {
                        StatusBadge("RUNNING", StatusType.WARNING)
                    } else {
                        StatusBadge("COMPLETE", StatusType.SUCCESS)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (progress.totalTasks > 0) {
                    Text(
                        "${progress.completedTasks} / ${progress.totalTasks} tasks" +
                            if (progress.isRunning) " \u2014 ${progress.currentTask}" else "",
                        fontSize = 12.sp, color = CortexColors.TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.completedTasks.toFloat() / progress.totalTasks },
                        color = if (progress.isRunning) CortexColors.PaloAltoOrange else CortexColors.Success,
                        trackColor = CortexColors.SurfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }

                progress.outputPath?.let { path ->
                    Spacer(Modifier.height(12.dp))
                    Text("Output:", fontSize = 11.sp, color = CortexColors.TextMuted)
                    Text(path.toString(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = CortexColors.Success)
                    Spacer(Modifier.height(4.dp))
                    val sizeMb = try { path.toFile().length() / 1_048_576.0 } catch (_: Exception) { 0.0 }
                    Text("Size: ${"%.1f".format(sizeMb)} MB", fontSize = 11.sp, color = CortexColors.TextMuted)
                }
            }
        }

        // Collected artifacts list
        if (progress.artifacts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
                    .padding(16.dp)
            ) {
                Text("Collected Artifacts (${progress.artifacts.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
                Spacer(Modifier.height(8.dp))

                progress.artifacts.forEach { name ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("\u2713 ", fontSize = 11.sp, color = CortexColors.Success)
                        Text(name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CortexColors.TextSecondary)
                    }
                }
            }
        }

        // Errors list
        if (progress.errors.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Error.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                    .border(1.dp, CortexColors.Error.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(16.dp)
            ) {
                Text("Failed Artifacts (${progress.errors.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.Error)
                Spacer(Modifier.height(8.dp))
                progress.errors.forEach { error ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("\u2717 ", fontSize = 11.sp, color = CortexColors.Error)
                        Text(error, fontSize = 10.sp, color = CortexColors.TextMuted)
                    }
                }
            }
        }

        // What's collected info
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, CortexColors.Border, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Text("Artifact Categories", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
            Spacer(Modifier.height(12.dp))

            ArtifactCategory("Cortex XDR Databases", listOf(
                "security_events.db — Alerts and prevention events",
                "hash_ioc.db — IoC blacklist entries",
                "wf_verdicts.db — WildFire verdict cache",
                "machine_learning_verdicts.db — ML verdict cache",
                "yara_verdicts.db — YARA rule verdicts",
                "hash_paths.db — File hash to path mappings",
                "quarantine.db — Quarantine records",
                "hash_overrides.db — Verdict overrides",
                "agent_settings.db — Agent configuration",
                "content_settings.db — Content/policy settings"
            ))

            Spacer(Modifier.height(12.dp))

            ArtifactCategory("Cortex XDR Status", listOf(
                "Agent info, protection status, runtime components",
                "Startup status, last check-in time"
            ))

            Spacer(Modifier.height(12.dp))

            ArtifactCategory("Windows Artifacts", listOf(
                "Scheduled tasks, services, startup items",
                "Installed software, firewall rules",
                "Network interfaces, TCP connections, DNS cache, ARP cache",
                "Running processes, user sessions",
                "Security Event Log (recent 100), Cortex XDR Event Log",
                "Prefetch file listing (execution evidence)"
            ))

            Spacer(Modifier.height(12.dp))

            ArtifactCategory("Agent Logs", listOf(
                "Log files from C:\\ProgramData\\Cyvera\\Logs\\"
            ))
        }
    }
}

@Composable
private fun ArtifactCategory(title: String, items: List<String>) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.PaloAltoOrange)
    Spacer(Modifier.height(4.dp))
    items.forEach { item ->
        Text("\u2022 $item", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
    }
}
