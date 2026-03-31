package com.cortex.localmanager.core.logs

import com.cortex.localmanager.core.config.AppConfig
import com.cortex.localmanager.core.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

class LogRepository(
    private val edrParser: EdrLogParser = EdrLogParser(),
    private val preventionParser: PreventionLogParser = PreventionLogParser(),
    private val logWatcher: LogWatcher
) {
    private val _alerts = MutableStateFlow<List<UnifiedAlert>>(emptyList())
    val alerts: StateFlow<List<UnifiedAlert>> = _alerts.asStateFlow()

    suspend fun loadExisting(logDirectory: Path = AppConfig.cyveraLogPath) {
        val allAlerts = mutableListOf<UnifiedAlert>()

        // Load EDR events
        edrParser.parseDirectory(logDirectory)
            .onSuccess { events ->
                allAlerts.addAll(events.map { it.toUnifiedAlert() })
            }
            .onFailure { logger.warn(it) { "Failed to load EDR events" } }

        // Load prevention XML alerts
        preventionParser.parseDirectory(logDirectory)
            .onSuccess { alerts ->
                allAlerts.addAll(alerts.map { it.toUnifiedAlert() })
            }
            .onFailure { logger.warn(it) { "Failed to load prevention alerts" } }

        // Load from Windows Event Log
        preventionParser.readFromEventLog()
            .onSuccess { alerts ->
                val eventLogAlerts = alerts.map { it.toUnifiedAlert(AlertSource.EVENT_LOG) }
                allAlerts.addAll(deduplicateEventLog(allAlerts, eventLogAlerts))
            }
            .onFailure { logger.debug(it) { "Event Log read skipped or failed" } }

        _alerts.value = allAlerts.sortedByDescending { it.timestamp }
        logger.info { "Loaded ${allAlerts.size} total alerts" }
    }

    fun startWatching(scope: CoroutineScope): Job {
        return scope.launch {
            logWatcher.events.collect { event ->
                when (event) {
                    is LogEvent.NewEdrLog -> {
                        val newAlerts = event.events.map { it.toUnifiedAlert() }
                        appendAlerts(newAlerts)
                    }
                    is LogEvent.NewPreventionAlert -> {
                        appendAlerts(listOf(event.alert.toUnifiedAlert()))
                    }
                    is LogEvent.ParseError -> {
                        logger.warn { "Parse error for ${event.path}: ${event.error}" }
                    }
                }
            }
        }
    }

    fun filter(criteria: FilterCriteria): List<UnifiedAlert> {
        var result = _alerts.value

        criteria.searchText?.takeIf { it.isNotBlank() }?.let { query ->
            val q = query.lowercase()
            result = result.filter { alert ->
                alert.processPath?.lowercase()?.contains(q) == true ||
                    alert.sha256?.lowercase()?.contains(q) == true ||
                    alert.description.lowercase().contains(q) ||
                    alert.user?.lowercase()?.contains(q) == true ||
                    alert.filePath?.lowercase()?.contains(q) == true ||
                    alert.commandLine?.lowercase()?.contains(q) == true ||
                    alert.componentName?.lowercase()?.contains(q) == true
            }
        }

        criteria.severities?.let { severities ->
            result = result.filter { it.severity in severities }
        }

        criteria.alertTypes?.let { types ->
            result = result.filter { it.alertType in types }
        }

        criteria.timeRange?.let { range ->
            if (range != TimeRange.ALL) {
                val cutoff = timeRangeCutoff(range)
                result = result.filter { it.timestamp >= cutoff }
            }
        }

        return result
    }

    private fun appendAlerts(newAlerts: List<UnifiedAlert>) {
        val current = _alerts.value.toMutableList()
        current.addAll(0, newAlerts) // newest first
        _alerts.value = current
    }

    /**
     * Deduplicate Event Log alerts that already appear in the XML alerts.
     * Match by timestamp + SHA256.
     */
    private fun deduplicateEventLog(
        existing: List<UnifiedAlert>,
        eventLogAlerts: List<UnifiedAlert>
    ): List<UnifiedAlert> {
        val existingKeys = existing
            .filter { it.sha256 != null }
            .map { "${it.timestamp.epochSeconds}-${it.sha256}" }
            .toSet()

        return eventLogAlerts.filter { alert ->
            if (alert.sha256 == null) true
            else "${alert.timestamp.epochSeconds}-${alert.sha256}" !in existingKeys
        }
    }

    private fun timeRangeCutoff(range: TimeRange): Instant {
        val now = Clock.System.now()
        return when (range) {
            TimeRange.LAST_1H -> now - 1.hours
            TimeRange.LAST_6H -> now - 6.hours
            TimeRange.LAST_24H -> now - 24.hours
            TimeRange.LAST_7D -> now - 7.days
            TimeRange.LAST_30D -> now - 30.days
            TimeRange.ALL -> Instant.DISTANT_PAST
        }
    }
}

// --- Conversion extensions ---

private fun EdrEvent.toUnifiedAlert(): UnifiedAlert {
    val parsedTimestamp = timestamp?.let {
        try { Instant.parse(it) } catch (_: Exception) { null }
    } ?: Clock.System.now()

    return UnifiedAlert(
        id = UUID.randomUUID().toString(),
        timestamp = parsedTimestamp,
        severity = mapSeverity(severity),
        alertType = AlertType.EDR,
        source = AlertSource.EDR_JSON,
        processPath = processPath,
        commandLine = commandLine,
        sha256 = sha256,
        md5 = null,
        filePath = filePath,
        user = user,
        description = description ?: eventType ?: "EDR Event",
        actionTaken = null,
        componentName = eventType,
        rawData = rawData?.toString()
    )
}

private fun PreventionAlert.toUnifiedAlert(
    source: AlertSource = AlertSource.LOCAL_XML
): UnifiedAlert {
    return UnifiedAlert(
        id = UUID.randomUUID().toString(),
        timestamp = timestamp,
        severity = mapPreventionSeverity(componentName, blocked),
        alertType = AlertType.PREVENTION,
        source = source,
        processPath = processPath,
        commandLine = null,
        sha256 = sha256,
        md5 = md5,
        filePath = filePath,
        user = user,
        description = description ?: operationStatus ?: "Prevention Alert",
        actionTaken = operationStatus,
        componentName = componentName,
        rawData = null
    )
}

private fun mapSeverity(severity: String?): Severity = when (severity?.lowercase()) {
    "critical" -> Severity.CRITICAL
    "high" -> Severity.HIGH
    "medium" -> Severity.MEDIUM
    "low" -> Severity.LOW
    "info", "informational" -> Severity.INFO
    else -> Severity.MEDIUM
}

private fun mapPreventionSeverity(component: String?, blocked: Boolean): Severity {
    if (blocked) {
        return when (component?.lowercase()) {
            "behavioralthreat" -> Severity.CRITICAL
            "wildfire" -> Severity.HIGH
            "localanalysis" -> Severity.HIGH
            else -> Severity.HIGH
        }
    }
    return Severity.MEDIUM
}
