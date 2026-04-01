package com.cortex.localmanager.ui.inventory

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cortex.localmanager.core.inventory.*
import com.cortex.localmanager.ui.components.StatusBadge
import com.cortex.localmanager.ui.components.StatusType
import com.cortex.localmanager.ui.theme.CortexColors
import java.awt.FileDialog
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun InventoryScreen(viewModel: InventoryViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: Collect + Export + Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .background(CortexColors.Surface, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            ActionBtn(if (state.isLoading) "Collecting..." else "Collect Inventory") {
                if (!state.isLoading) viewModel.collect()
            }

            if (state.inventory != null) {
                Spacer(Modifier.width(8.dp))
                ActionBtn("Export JSON") {
                    val path = showSaveDialog("json", "inventory") ?: return@ActionBtn
                    viewModel.exportAsJson(path)
                }
                if (state.activeTab in setOf(InventoryTab.PROCESSES, InventoryTab.SESSIONS, InventoryTab.CONNECTIONS)) {
                    Spacer(Modifier.width(4.dp))
                    ActionBtn("Export CSV") {
                        val path = showSaveDialog("csv", state.activeTab.name.lowercase()) ?: return@ActionBtn
                        viewModel.exportAsCsv(path, state.activeTab)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            state.inventory?.let {
                Text(
                    "Collected at ${it.collectedAt.toString().take(19).replace("T", " ")} UTC",
                    fontSize = 10.sp, color = CortexColors.TextMuted
                )
            }
        }

        // Messages
        state.error?.let { error ->
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.Error.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("\u26A0 $error", fontSize = 11.sp, color = CortexColors.Error)
            }
        }
        state.exportMessage?.let { msg ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("\u2713 $msg", fontSize = 11.sp, color = CortexColors.Success)
                Spacer(Modifier.width(8.dp))
                Text("\u2715", fontSize = 11.sp, color = CortexColors.TextMuted, modifier = Modifier.clickable { viewModel.dismissMessage() })
            }
        }

        if (state.isLoading) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                color = CortexColors.PaloAltoOrange,
                trackColor = CortexColors.SurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.inventory == null && !state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
                    .background(CortexColors.Surface, RoundedCornerShape(4.dp))
            ) {
                Text("Click 'Collect Inventory' to gather endpoint information", fontSize = 14.sp, color = CortexColors.TextMuted)
            }
            return
        }

        val inv = state.inventory ?: return

        // Tab bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(CortexColors.Surface, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 8.dp)
        ) {
            InventoryTab.entries.forEach { tab ->
                val isActive = tab == state.activeTab
                val label = when (tab) {
                    InventoryTab.SYSTEM -> "System"
                    InventoryTab.PROCESSES -> "Processes (${inv.processes.size})"
                    InventoryTab.SESSIONS -> "Sessions (${inv.sessions.size})"
                    InventoryTab.NETWORK -> "Network (${inv.network.size})"
                    InventoryTab.CONNECTIONS -> "Connections (${inv.networkConnections.size})"
                }
                Box(
                    modifier = Modifier
                        .clickable { viewModel.setTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .then(
                            if (isActive) Modifier.border(
                                width = 2.dp,
                                color = CortexColors.PaloAltoOrange,
                                shape = RoundedCornerShape(0.dp)
                            ) else Modifier
                        )
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) CortexColors.PaloAltoOrange else CortexColors.TextMuted
                    )
                }
            }
        }

        // Tab content
        Box(
            modifier = Modifier.fillMaxSize()
                .background(CortexColors.Surface, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
        ) {
            when (state.activeTab) {
                InventoryTab.SYSTEM -> SystemTab(inv.system)
                InventoryTab.PROCESSES -> ProcessesTab(inv.processes, state.processFilter, viewModel::setProcessFilter)
                InventoryTab.SESSIONS -> SessionsTab(inv.sessions)
                InventoryTab.NETWORK -> NetworkTab(inv.network)
                InventoryTab.CONNECTIONS -> ConnectionsTab(inv.networkConnections, state.connectionFilter, viewModel::setConnectionFilter)
            }
        }
    }
}

// --- System Tab ---

@Composable
private fun SystemTab(system: SystemInfo) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("Operating System")
        InfoRow("Hostname", system.hostname)
        system.domain?.let { InfoRow("Domain", it) }
        system.osName?.let { InfoRow("OS", it) }
        system.osVersion?.let { InfoRow("Version", it) }
        system.osBuild?.let { InfoRow("Build", it) }
        system.architecture?.let { InfoRow("Architecture", it) }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Hardware")
        system.manufacturer?.let { InfoRow("Manufacturer", it) }
        system.model?.let { InfoRow("Model", it) }
        system.cpuName?.let { InfoRow("CPU", it) }
        system.cpuCores?.let { InfoRow("CPU Cores", it.toString()) }
        system.totalMemoryMb?.let { InfoRow("Total Memory", "${it} MB") }
        system.availableMemoryMb?.let {
            val total = system.totalMemoryMb ?: 1
            val pct = if (total > 0) ((total - it) * 100 / total) else 0
            InfoRow("Available Memory", "${it} MB ($pct% used)")
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Session")
        system.currentUser?.let { InfoRow("Current User", it) }
        system.lastBoot?.let { InfoRow("Last Boot", it) }
        system.systemDrive?.let { InfoRow("System Drive", it) }
    }
}

// --- Processes Tab ---

@Composable
private fun ProcessesTab(processes: List<RunningProcess>, filter: String, onFilterChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp)
                .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (filter.isEmpty()) Text("Filter processes by name or path...", fontSize = 12.sp, color = CortexColors.TextMuted)
            BasicTextField(
                value = filter, onValueChange = onFilterChange, singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = processes.filter {
            filter.isBlank() || it.name.contains(filter, ignoreCase = true) ||
                (it.path?.contains(filter, ignoreCase = true) == true)
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            HeaderCell("PID", 70.dp)
            HeaderCell("NAME", null, 1f)
            HeaderCell("MEMORY", 100.dp)
            HeaderCell("PATH", null, 1.5f)
            HeaderCell("PPID", 70.dp)
        }

        // Rows — use scrollable column
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            filtered.take(500).forEachIndexed { index, proc ->
                val bg = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    CellText(proc.pid.toString(), 70.dp, mono = true)
                    CellText(proc.name, weight = 1f)
                    CellText(proc.memoryKb?.let { formatMemory(it) } ?: "-", 100.dp)
                    CellText(proc.path ?: "-", weight = 1.5f, mono = true, muted = true)
                    CellText(proc.parentPid?.toString() ?: "-", 70.dp, mono = true)
                }
            }
            if (filtered.size > 500) {
                Text("Showing 500 of ${filtered.size}. Use filter to narrow.", fontSize = 10.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// --- Sessions Tab ---

@Composable
private fun SessionsTab(sessions: List<UserSession>) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            HeaderCell("USERNAME", null, 1f)
            HeaderCell("SESSION", 100.dp)
            HeaderCell("ID", 50.dp)
            HeaderCell("STATE", 100.dp)
            HeaderCell("IDLE", 80.dp)
            HeaderCell("LOGON TIME", null, 1f)
        }

        sessions.forEachIndexed { index, session ->
            val bg = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                CellText(session.username, weight = 1f, bold = true)
                CellText(session.sessionName ?: "-", 100.dp)
                CellText(session.sessionId.toString(), 50.dp, mono = true)
                Box(Modifier.width(100.dp)) {
                    StatusBadge(
                        session.state,
                        when (session.state.lowercase()) {
                            "active" -> StatusType.SUCCESS
                            "disc", "disconnected" -> StatusType.WARNING
                            else -> StatusType.INFO
                        }
                    )
                }
                CellText(session.idleTime ?: "-", 80.dp)
                CellText(session.logonTime ?: "-", weight = 1f)
            }
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No active sessions found", fontSize = 13.sp, color = CortexColors.TextMuted)
            }
        }
    }
}

// --- Network Tab ---

@Composable
private fun NetworkTab(interfaces: List<NetworkInterface>) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (interfaces.isEmpty()) {
            Text("No active network interfaces found", fontSize = 13.sp, color = CortexColors.TextMuted)
            return
        }

        interfaces.forEachIndexed { index, iface ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(iface.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CortexColors.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    iface.status?.let { StatusBadge(it, StatusType.SUCCESS) }
                }
                Spacer(Modifier.height(8.dp))

                iface.macAddress?.let { InfoRow("MAC", it, mono = true) }
                if (iface.ipAddresses.isNotEmpty()) {
                    InfoRow("IP", iface.ipAddresses.joinToString(", "), mono = true)
                }
                iface.subnet?.let { InfoRow("Subnet", it) }
                iface.gateway?.let { InfoRow("Gateway", it, mono = true) }
                if (iface.dnsServers.isNotEmpty()) {
                    InfoRow("DNS", iface.dnsServers.joinToString(", "), mono = true)
                }
            }
            if (index < interfaces.size - 1) Spacer(Modifier.height(8.dp))
        }
    }
}

// --- Connections Tab ---

@Composable
private fun ConnectionsTab(connections: List<NetworkConnection>, filter: String, onFilterChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp)
                .background(CortexColors.SurfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (filter.isEmpty()) Text("Filter by address, port, state, or PID...", fontSize = 12.sp, color = CortexColors.TextMuted)
            BasicTextField(
                value = filter, onValueChange = onFilterChange, singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = CortexColors.TextPrimary),
                cursorBrush = SolidColor(CortexColors.PaloAltoOrange),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = connections.filter { c ->
            filter.isBlank() ||
                c.localAddress.contains(filter, ignoreCase = true) ||
                c.localPort.toString().contains(filter) ||
                (c.remoteAddress?.contains(filter, ignoreCase = true) == true) ||
                (c.state?.contains(filter, ignoreCase = true) == true) ||
                (c.pid?.toString()?.contains(filter) == true)
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(CortexColors.SurfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            HeaderCell("PROTO", 50.dp)
            HeaderCell("LOCAL ADDRESS", null, 1f)
            HeaderCell("REMOTE ADDRESS", null, 1f)
            HeaderCell("STATE", 110.dp)
            HeaderCell("PID", 70.dp)
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            filtered.take(500).forEachIndexed { index, conn ->
                val bg = if (index % 2 == 0) CortexColors.Surface else CortexColors.Background
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    CellText(conn.protocol, 50.dp)
                    CellText("${conn.localAddress}:${conn.localPort}", weight = 1f, mono = true)
                    CellText(
                        if (conn.remoteAddress != null) "${conn.remoteAddress}:${conn.remotePort ?: ""}" else "-",
                        weight = 1f, mono = true
                    )
                    Box(Modifier.width(110.dp)) {
                        val state = conn.state
                        if (state != null) {
                            StatusBadge(state, when (state) {
                                "ESTABLISHED" -> StatusType.SUCCESS
                                "LISTENING" -> StatusType.INFO
                                "TIME_WAIT", "CLOSE_WAIT" -> StatusType.WARNING
                                else -> StatusType.INFO
                            })
                        } else Text("-", fontSize = 11.sp, color = CortexColors.TextMuted)
                    }
                    CellText(conn.pid?.toString() ?: "-", 70.dp, mono = true)
                }
            }
            if (filtered.size > 500) {
                Text("Showing 500 of ${filtered.size}", fontSize = 10.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// --- Shared components ---

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        Text("$label:", fontSize = 12.sp, color = CortexColors.TextMuted, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 12.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, color = CortexColors.TextPrimary)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: Dp? = null, weight: Float? = null) {
    val mod = when {
        width != null -> Modifier.width(width)
        weight != null -> Modifier.weight(weight)
        else -> Modifier
    }
    Box(mod) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = CortexColors.TextMuted)
    }
}

@Composable
private fun RowScope.CellText(text: String, width: Dp? = null, weight: Float? = null, mono: Boolean = false, muted: Boolean = false, bold: Boolean = false) {
    val mod = when {
        width != null -> Modifier.width(width)
        weight != null -> Modifier.weight(weight)
        else -> Modifier
    }
    Text(
        text, maxLines = 1, fontSize = 11.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (muted) CortexColors.TextMuted else CortexColors.TextSecondary,
        modifier = mod
    )
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
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CortexColors.PaloAltoOrange)
    }
}

private fun formatMemory(kb: Long): String = when {
    kb >= 1_048_576 -> "%.1f GB".format(kb / 1_048_576.0)
    kb >= 1024 -> "%.1f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

private fun showSaveDialog(extension: String, prefix: String): java.nio.file.Path? {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val hostname = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "endpoint" }
    val defaultName = "cortex_${prefix}_${hostname}_$timestamp.$extension"

    val dialog = FileDialog(null as java.awt.Frame?, "Export Inventory", FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true

    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file).toPath()
}
