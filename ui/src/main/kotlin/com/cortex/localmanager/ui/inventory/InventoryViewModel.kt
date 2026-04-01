package com.cortex.localmanager.ui.inventory

import com.cortex.localmanager.core.inventory.EndpointInventory
import com.cortex.localmanager.core.inventory.EndpointInventoryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.writeText

enum class InventoryTab { SYSTEM, PROCESSES, SESSIONS, NETWORK, CONNECTIONS }

data class InventoryState(
    val isLoading: Boolean = false,
    val inventory: EndpointInventory? = null,
    val error: String? = null,
    val activeTab: InventoryTab = InventoryTab.SYSTEM,
    val processFilter: String = "",
    val connectionFilter: String = "",
    val exportMessage: String? = null
)

class InventoryViewModel(
    private val collector: EndpointInventoryCollector = EndpointInventoryCollector(),
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    fun collect() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val inventory = collector.collect()
                _state.update { it.copy(inventory = inventory, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Collection failed: ${e.message}") }
            }
        }
    }

    fun setTab(tab: InventoryTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun setProcessFilter(filter: String) {
        _state.update { it.copy(processFilter = filter) }
    }

    fun setConnectionFilter(filter: String) {
        _state.update { it.copy(connectionFilter = filter) }
    }

    fun dismissMessage() {
        _state.update { it.copy(exportMessage = null, error = null) }
    }

    fun exportAsJson(outputPath: Path): Result<Path> {
        val inv = _state.value.inventory ?: return Result.failure(IllegalStateException("No data"))
        return runCatching {
            val sb = StringBuilder("{\n")
            sb.appendLine("  \"collectedAt\": \"${inv.collectedAt}\",")

            // System
            sb.appendLine("  \"system\": {")
            with(inv.system) {
                sb.appendLine("    \"hostname\": ${jsonStr(hostname)},")
                sb.appendLine("    \"domain\": ${jsonStr(domain)},")
                sb.appendLine("    \"osName\": ${jsonStr(osName)},")
                sb.appendLine("    \"osVersion\": ${jsonStr(osVersion)},")
                sb.appendLine("    \"osBuild\": ${jsonStr(osBuild)},")
                sb.appendLine("    \"architecture\": ${jsonStr(architecture)},")
                sb.appendLine("    \"manufacturer\": ${jsonStr(manufacturer)},")
                sb.appendLine("    \"model\": ${jsonStr(model)},")
                sb.appendLine("    \"totalMemoryMb\": $totalMemoryMb,")
                sb.appendLine("    \"availableMemoryMb\": $availableMemoryMb,")
                sb.appendLine("    \"cpuName\": ${jsonStr(cpuName)},")
                sb.appendLine("    \"cpuCores\": $cpuCores,")
                sb.appendLine("    \"lastBoot\": ${jsonStr(lastBoot)},")
                sb.appendLine("    \"currentUser\": ${jsonStr(currentUser)}")
            }
            sb.appendLine("  },")

            // Processes
            sb.appendLine("  \"processes\": [")
            inv.processes.forEachIndexed { i, p ->
                sb.append("    {\"pid\":${p.pid},\"name\":${jsonStr(p.name)},\"memoryKb\":${p.memoryKb},\"path\":${jsonStr(p.path)},\"parentPid\":${p.parentPid}}")
                if (i < inv.processes.size - 1) sb.append(",")
                sb.appendLine()
            }
            sb.appendLine("  ],")

            // Sessions
            sb.appendLine("  \"sessions\": [")
            inv.sessions.forEachIndexed { i, s ->
                sb.append("    {\"username\":${jsonStr(s.username)},\"sessionId\":${s.sessionId},\"state\":${jsonStr(s.state)},\"logonTime\":${jsonStr(s.logonTime)}}")
                if (i < inv.sessions.size - 1) sb.append(",")
                sb.appendLine()
            }
            sb.appendLine("  ],")

            // Network
            sb.appendLine("  \"network\": [")
            inv.network.forEachIndexed { i, n ->
                sb.append("    {\"name\":${jsonStr(n.name)},\"mac\":${jsonStr(n.macAddress)},\"ip\":${jsonStr(n.ipAddresses.joinToString(","))}}")
                if (i < inv.network.size - 1) sb.append(",")
                sb.appendLine()
            }
            sb.appendLine("  ],")

            // Connections
            sb.appendLine("  \"connections\": [")
            inv.networkConnections.forEachIndexed { i, c ->
                sb.append("    {\"proto\":${jsonStr(c.protocol)},\"local\":${jsonStr("${c.localAddress}:${c.localPort}")},\"remote\":${jsonStr("${c.remoteAddress ?: ""}:${c.remotePort ?: ""}")},\"state\":${jsonStr(c.state)},\"pid\":${c.pid}}")
                if (i < inv.networkConnections.size - 1) sb.append(",")
                sb.appendLine()
            }
            sb.appendLine("  ]")

            sb.appendLine("}")
            outputPath.writeText(sb.toString())
            _state.update { it.copy(exportMessage = "Exported inventory to ${outputPath.fileName}") }
            outputPath
        }
    }

    fun exportAsCsv(outputPath: Path, tab: InventoryTab): Result<Path> {
        val inv = _state.value.inventory ?: return Result.failure(IllegalStateException("No data"))
        return runCatching {
            val content = when (tab) {
                InventoryTab.PROCESSES -> {
                    val header = "PID,Name,MemoryKB,Path,ParentPID,SessionID"
                    val rows = inv.processes.joinToString("\n") { p ->
                        "${p.pid},${csvEsc(p.name)},${p.memoryKb ?: ""},${csvEsc(p.path)},${p.parentPid ?: ""},${p.sessionId ?: ""}"
                    }
                    "$header\n$rows"
                }
                InventoryTab.SESSIONS -> {
                    val header = "Username,SessionName,SessionID,State,IdleTime,LogonTime"
                    val rows = inv.sessions.joinToString("\n") { s ->
                        "${csvEsc(s.username)},${csvEsc(s.sessionName)},${s.sessionId},${s.state},${csvEsc(s.idleTime)},${csvEsc(s.logonTime)}"
                    }
                    "$header\n$rows"
                }
                InventoryTab.CONNECTIONS -> {
                    val header = "Protocol,LocalAddress,LocalPort,RemoteAddress,RemotePort,State,PID"
                    val rows = inv.networkConnections.joinToString("\n") { c ->
                        "${c.protocol},${c.localAddress},${c.localPort},${c.remoteAddress ?: ""},${c.remotePort ?: ""},${c.state ?: ""},${c.pid ?: ""}"
                    }
                    "$header\n$rows"
                }
                else -> "No CSV export for this tab"
            }
            outputPath.writeText(content)
            _state.update { it.copy(exportMessage = "Exported to ${outputPath.fileName}") }
            outputPath
        }
    }
}

private fun jsonStr(value: String?): String =
    if (value == null) "null" else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun csvEsc(value: String?): String {
    if (value == null) return ""
    return if (value.contains(",") || value.contains("\"") || value.contains("\n"))
        "\"${value.replace("\"", "\"\"")}\"" else value
}
