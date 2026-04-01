package com.cortex.localmanager.ui.agentcontrol

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

@Composable
fun AgentControlScreen(viewModel: AgentControlViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Messages
        state.error?.let {
            Box(Modifier.fillMaxWidth().background(CortexColors.Error.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).padding(12.dp)) {
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

        if (state.isLoading) {
            LinearProgressIndicator(color = CortexColors.PaloAltoOrange, trackColor = CortexColors.SurfaceVariant, modifier = Modifier.fillMaxWidth().height(3.dp))
            Spacer(Modifier.height(8.dp))
        }

        // Section 1: Tamper Protection
        SectionCard("Tamper Protection (Self-Protection)", "Controls protection of Cortex XDR's own processes, registry keys, files, and services. Disable temporarily for maintenance. Changes persist until next cloud check-in.") {
            state.protectionFeatures.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feature.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CortexColors.TextPrimary)
                        Text("Mode: ${feature.mode}", fontSize = 10.sp, color = CortexColors.TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(
                            if (feature.enabled) "Enabled" else "Disabled",
                            if (feature.enabled) StatusType.SUCCESS else StatusType.ERROR
                        )
                        if (feature.enabled) {
                            ActionBtn("Disable", CortexColors.Warning) { viewModel.disableProtection(feature.name.lowercase()) }
                        } else {
                            ActionBtn("Enable", CortexColors.Success) { viewModel.enableProtection(feature.name.lowercase()) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ActionBtn("Reset All to Policy", CortexColors.Info) { viewModel.resetProtectionToPolicy() }
        }

        Spacer(Modifier.height(16.dp))

        // Section 2: Runtime Components
        SectionCard("Agent Components", "Start/stop individual agent components. Stopping core components will reduce protection. Tamper protection must be disabled first for some operations.") {
            state.runtimeComponents.forEach { comp ->
                val isRunning = comp.state.equals("Running", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (isRunning) Color.Transparent else CortexColors.Error.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(comp.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = CortexColors.TextPrimary)
                        Text(comp.description, fontSize = 10.sp, color = CortexColors.TextMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(comp.state, if (isRunning) StatusType.SUCCESS else StatusType.ERROR)
                        if (isRunning) {
                            ActionBtn("Stop", CortexColors.Error) { viewModel.stopComponent(comp.name) }
                        } else {
                            ActionBtn("Start", CortexColors.Success) { viewModel.startComponent(comp.name) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("Stop All", CortexColors.Error) { viewModel.stopAllComponents() }
                ActionBtn("Start All", CortexColors.Success) { viewModel.startAllComponents() }
                ActionBtn("Refresh", CortexColors.PaloAltoOrange) { viewModel.refresh() }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
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
        content()
    }
}

@Composable
private fun ActionBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color) }
}
