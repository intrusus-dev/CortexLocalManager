package com.cortex.localmanager.ui

import androidx.compose.runtime.MutableState
import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolExecutor
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.logs.EdrLogParser
import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.logs.LogWatcher
import com.cortex.localmanager.core.logs.PreventionLogParser
import com.cortex.localmanager.core.suex.SuexManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun log(msg: String) {
    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    println("$time [SecurityEvents] $msg")
}

class ServiceLocator(val supervisorPassword: MutableState<String?>) {
    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    val cytoolExecutor by lazy { CytoolExecutor() }
    val cytoolCommands by lazy {
        CytoolCommands(cytoolExecutor, passwordProvider = { supervisorPassword.value })
    }
    val edrLogParser by lazy { EdrLogParser() }
    val preventionLogParser by lazy { PreventionLogParser() }
    val logWatcher by lazy {
        LogWatcher(
            edrLogParser = edrLogParser,
            preventionLogParser = preventionLogParser,
            scope = backgroundScope
        )
    }
    val logRepository by lazy {
        LogRepository(edrLogParser, preventionLogParser, logWatcher)
    }
    val suexManager by lazy { SuexManager() }

    fun initialize() {
        backgroundScope.launch {
            // Load existing logs from disk and Windows Event Log
            logRepository.loadExisting()
            // Start watching for new log files
            logWatcher.start()
            logRepository.startWatching(backgroundScope)
        }
    }

    private val _securityEventsStatus = MutableStateFlow<String?>(null)
    val securityEventsError: StateFlow<String?> = _securityEventsStatus

    /**
     * Load rich security events from cytool persist print security_events.db.
     * Call when password becomes available.
     */
    fun loadSecurityEvents() {
        backgroundScope.launch {
            _securityEventsStatus.value = null
            log("Fetching security events via persist print security_events.db...")

            try {
                val password = supervisorPassword.value
                if (password == null) {
                    _securityEventsStatus.value = "Supervisor password not set"
                    return@launch
                }

                // Use persist print security_events.db directly — proven reliable
                // Use longer timeout since the output can be very large
                val result = cytoolCommands.executor.execute(
                    "persist", "print", "security_events.db",
                    password = password,
                    timeoutSeconds = 120
                )

                when (result) {
                    is CytoolResult.Success -> {
                        val json = extractSecurityEventsJson(result.data)
                        if (json != null) {
                            logRepository.loadSecurityEvents(json)
                            val alertCount = logRepository.alerts.value.count {
                                it.source == com.cortex.localmanager.core.models.AlertSource.SECURITY_EVENTS
                            }
                            log("Security events loaded: $alertCount alerts from security_events.db")
                            _securityEventsStatus.value = null
                        } else {
                            val msg = "No JSON data found in security_events.db output"
                            log(msg)
                            _securityEventsStatus.value = msg
                        }
                    }
                    is CytoolResult.Error -> {
                        val msg = "Failed to load security events: ${result.message}"
                        log("$msg\nRaw output: ${result.rawOutput.take(500)}")
                        _securityEventsStatus.value = msg
                    }
                    is CytoolResult.Timeout -> {
                        val msg = "Security events fetch timed out (120s)"
                        log(msg)
                        _securityEventsStatus.value = msg
                    }
                }
            } catch (e: Exception) {
                val msg = "Error loading security events: ${e.message}"
                log("$msg: ${e.stackTraceToString().take(500)}")
                _securityEventsStatus.value = msg
            }
        }
    }

    /**
     * Extract JSON array from persist print output.
     * Strips preamble ("Database ... Enter supervisor password:") and
     * suffix ("Iterated [N] entries.").
     */
    private fun extractSecurityEventsJson(output: String): String? {
        val lines = output.lines()
        val jsonStart = lines.indexOfFirst { it.trimStart().startsWith("[") }
        if (jsonStart == -1) {
            log("No JSON array found in persist output. First 500 chars: ${output.take(500)}")
            return null
        }

        val sb = StringBuilder()
        for (i in jsonStart until lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("Iterated [")) break
            sb.appendLine(line)
        }

        val json = sb.toString().trim()
        if (json.isEmpty()) {
            log("Empty JSON after extraction")
            return null
        }

        log("Extracted security events JSON: ${json.length} chars")
        return json
    }
}
