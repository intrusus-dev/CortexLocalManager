package com.cortex.localmanager.ui.detections

import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.writeText

enum class SortColumn { TIME, SEVERITY, TYPE, COMPONENT, PROCESS, DESCRIPTION }

data class DetectionsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val allAlerts: List<UnifiedAlert> = emptyList(),
    val filteredAlerts: List<UnifiedAlert> = emptyList(),
    val selectedAlert: UnifiedAlert? = null,
    val searchQuery: String = "",
    val activeSeverities: Set<Severity> = Severity.entries.toSet(),
    val activeTypes: Set<AlertType> = AlertType.entries.toSet(),
    val timeRange: TimeRange = TimeRange.LAST_24H,
    val sortColumn: SortColumn = SortColumn.TIME,
    val sortAscending: Boolean = false,
    val exportMessage: String? = null
)

class DetectionsViewModel(
    private val logRepository: LogRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(DetectionsState())
    val state: StateFlow<DetectionsState> = _state.asStateFlow()

    init {
        scope.launch {
            logRepository.alerts.collect { alerts ->
                _state.update {
                    it.copy(allAlerts = alerts, isLoading = false)
                }
                applyFilters()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleSeverityFilter(severity: Severity) {
        _state.update {
            val current = it.activeSeverities.toMutableSet()
            if (severity in current) current.remove(severity) else current.add(severity)
            it.copy(activeSeverities = current)
        }
        applyFilters()
    }

    fun toggleTypeFilter(type: AlertType) {
        _state.update {
            val current = it.activeTypes.toMutableSet()
            if (type in current) current.remove(type) else current.add(type)
            it.copy(activeTypes = current)
        }
        applyFilters()
    }

    fun setTimeRange(range: TimeRange) {
        _state.update { it.copy(timeRange = range) }
        applyFilters()
    }

    fun setSort(column: SortColumn) {
        _state.update {
            if (it.sortColumn == column) {
                it.copy(sortAscending = !it.sortAscending)
            } else {
                it.copy(sortColumn = column, sortAscending = true)
            }
        }
        applyFilters()
    }

    fun selectAlert(alert: UnifiedAlert?) {
        _state.update { it.copy(selectedAlert = alert) }
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            logRepository.loadExisting()
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun dismissExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    fun exportAsJson(outputPath: Path, filteredOnly: Boolean): Result<Path> {
        val alerts = if (filteredOnly) _state.value.filteredAlerts else _state.value.allAlerts
        return runCatching {
            val entries = alerts.map { it.toExportMap() }
            val jsonStr = buildJsonArray(entries)
            outputPath.writeText(jsonStr)
            _state.update { it.copy(exportMessage = "Exported ${alerts.size} alerts to ${outputPath.fileName}") }
            outputPath
        }
    }

    fun exportAsCsv(outputPath: Path, filteredOnly: Boolean): Result<Path> {
        val alerts = if (filteredOnly) _state.value.filteredAlerts else _state.value.allAlerts
        return runCatching {
            val header = "Timestamp,Severity,Type,Component,ProcessPath,Description,SHA256,MD5,FilePath,User,UserSID,PID,ParentPID,Action,Source"
            val rows = alerts.joinToString("\n") { a ->
                listOf(
                    a.timestamp.toString(),
                    a.severity.name,
                    a.alertType.name,
                    a.componentName ?: "",
                    csvEscape(a.processPath),
                    csvEscape(a.description),
                    a.sha256 ?: "",
                    a.md5 ?: "",
                    csvEscape(a.filePath),
                    csvEscape(a.user),
                    a.userSid ?: "",
                    a.pid?.toString() ?: "",
                    a.parentPid?.toString() ?: "",
                    a.actionTaken ?: "",
                    a.source.name
                ).joinToString(",")
            }
            outputPath.writeText("$header\n$rows")
            _state.update { it.copy(exportMessage = "Exported ${alerts.size} alerts to ${outputPath.fileName}") }
            outputPath
        }
    }

    private fun applyFilters() {
        val s = _state.value
        val criteria = FilterCriteria(
            searchText = s.searchQuery.takeIf { it.isNotBlank() },
            severities = s.activeSeverities,
            alertTypes = s.activeTypes,
            timeRange = s.timeRange
        )
        var filtered = logRepository.filter(criteria)

        // Apply sorting
        filtered = when (s.sortColumn) {
            SortColumn.TIME -> filtered.sortedBy { it.timestamp }
            SortColumn.SEVERITY -> filtered.sortedBy { it.severity.ordinal }
            SortColumn.TYPE -> filtered.sortedBy { it.alertType.name }
            SortColumn.COMPONENT -> filtered.sortedBy { it.componentName ?: "" }
            SortColumn.PROCESS -> filtered.sortedBy { it.processPath ?: it.filePath ?: "" }
            SortColumn.DESCRIPTION -> filtered.sortedBy { it.description }
        }
        if (!s.sortAscending) filtered = filtered.reversed()

        _state.update { it.copy(filteredAlerts = filtered) }
    }
}

private fun UnifiedAlert.toExportMap(): Map<String, String?> = mapOf(
    "id" to id,
    "timestamp" to timestamp.toString(),
    "severity" to severity.name,
    "alertType" to alertType.name,
    "source" to source.name,
    "processPath" to processPath,
    "commandLine" to commandLine,
    "pid" to pid?.toString(),
    "parentPid" to parentPid?.toString(),
    "sha256" to sha256,
    "md5" to md5,
    "filePath" to filePath,
    "fileSize" to fileSize?.toString(),
    "user" to user,
    "userSid" to userSid,
    "description" to description,
    "actionTaken" to actionTaken,
    "componentName" to componentName,
    "applicationName" to applicationName,
    "publisher" to publisher
)

private fun csvEscape(value: String?): String {
    if (value == null) return ""
    return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value
}

private fun buildJsonArray(entries: List<Map<String, String?>>): String {
    val sb = StringBuilder("[\n")
    entries.forEachIndexed { index, map ->
        sb.append("  {\n")
        val fields = map.entries.toList()
        fields.forEachIndexed { i, (key, value) ->
            val jsonValue = if (value == null) "null" else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            sb.append("    \"$key\": $jsonValue")
            if (i < fields.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  }")
        if (index < entries.size - 1) sb.append(",")
        sb.append("\n")
    }
    sb.append("]")
    return sb.toString()
}
