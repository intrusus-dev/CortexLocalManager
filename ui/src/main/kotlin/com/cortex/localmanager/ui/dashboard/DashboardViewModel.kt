package com.cortex.localmanager.ui.dashboard

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DashboardState(
    val isLoading: Boolean = true,
    val agentInfo: AgentInfo? = null,
    val agentInfoError: String? = null,
    val protectionFeatures: List<ProtectionFeature> = emptyList(),
    val protectionError: String? = null,
    val lastCheckinDisplay: String? = null,
    val lastCheckinTimestamp: String? = null,
    val lastCheckinError: String? = null,
    val isOffline: Boolean = false,
    val recentAlerts: List<UnifiedAlert> = emptyList(),
    val checkinInProgress: Boolean = false
)

class DashboardViewModel(
    private val cytoolCommands: CytoolCommands,
    private val logRepository: LogRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        refresh()
        startAutoRefresh()
        // Collect alerts from repository
        scope.launch {
            logRepository.alerts.collect { alerts ->
                _state.update { it.copy(recentAlerts = alerts.take(50)) }
            }
        }
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }

            // Parallel fetch — each card handles its own error
            val infoDeferred = async { cytoolCommands.queryInfo() }
            val protectDeferred = async { cytoolCommands.queryProtection() }
            val checkinDeferred = async { cytoolCommands.lastCheckin() }

            // Agent info
            when (val result = infoDeferred.await()) {
                is CytoolResult.Success -> _state.update {
                    it.copy(agentInfo = result.data, agentInfoError = null)
                }
                is CytoolResult.Error -> _state.update {
                    it.copy(agentInfoError = result.message)
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(agentInfoError = "Timed out")
                }
            }

            // Protection status
            when (val result = protectDeferred.await()) {
                is CytoolResult.Success -> _state.update {
                    it.copy(protectionFeatures = result.data, protectionError = null)
                }
                is CytoolResult.Error -> _state.update {
                    it.copy(protectionError = result.message)
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(protectionError = "Timed out")
                }
            }

            // Last check-in
            when (val result = checkinDeferred.await()) {
                is CytoolResult.Success -> {
                    val (displayText, timestamp) = result.data
                    val isOffline = checkIfOffline(timestamp)
                    _state.update {
                        it.copy(
                            lastCheckinDisplay = displayText,
                            lastCheckinTimestamp = timestamp,
                            lastCheckinError = null,
                            isOffline = isOffline
                        )
                    }
                }
                is CytoolResult.Error -> _state.update {
                    it.copy(lastCheckinError = result.message, isOffline = true)
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(lastCheckinError = "Timed out", isOffline = true)
                }
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    fun forceCheckin() {
        scope.launch {
            _state.update { it.copy(checkinInProgress = true) }
            cytoolCommands.checkin()
            delay(2.seconds)
            _state.update { it.copy(checkinInProgress = false) }
            refresh()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (isActive) {
                delay(60.seconds)
                refresh()
            }
        }
    }

    fun stop() {
        autoRefreshJob?.cancel()
    }

    private fun checkIfOffline(checkinTimestamp: String?): Boolean {
        if (checkinTimestamp == null) return true
        return try {
            val instant = parseTimestamp(checkinTimestamp) ?: return true
            (Clock.System.now() - instant) > 10.minutes
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Parse timestamps from cytool in various formats:
     * - ISO with Z: "2026-03-31T14:24:31Z"
     * - ISO without Z (cytool outputs UTC without suffix): "2026-03-31T14:24:31"
     * - US date: "3/31/2026 4:24:31 PM"
     */
    private fun parseTimestamp(text: String): Instant? {
        // Try ISO with Z
        try { return Instant.parse(text) } catch (_: Exception) {}

        // Try ISO without Z — cytool outputs UTC, just missing the Z
        try { return Instant.parse("${text.trim()}Z") } catch (_: Exception) {}

        // Try US date format
        return parseUSDateTime(text)
    }

    /**
     * Parse US-style datetime like "3/31/2026 12:30:00 PM"
     */
    private fun parseUSDateTime(text: String): Instant? {
        val regex = Regex("""(\d{1,2})/(\d{1,2})/(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(text) ?: return null

        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        var hour = match.groupValues[4].toInt()
        val minute = match.groupValues[5].toInt()
        val second = match.groupValues[6].toInt()
        val ampm = match.groupValues[7].uppercase()

        if (ampm == "PM" && hour < 12) hour += 12
        if (ampm == "AM" && hour == 12) hour = 0

        return try {
            LocalDateTime(year, month, day, hour, minute, second)
                .toInstant(TimeZone.currentSystemDefault())
        } catch (_: Exception) {
            null
        }
    }
}
