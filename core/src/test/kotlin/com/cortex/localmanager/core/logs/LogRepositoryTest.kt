package com.cortex.localmanager.core.logs

import com.cortex.localmanager.core.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LogRepositoryTest {

    private fun createTestAlerts(): List<UnifiedAlert> {
        val now = Clock.System.now()
        return listOf(
            makeAlert(
                id = "1",
                timestamp = now - 30.minutes,
                severity = Severity.CRITICAL,
                alertType = AlertType.PREVENTION,
                description = "Malware blocked by LocalAnalysis",
                sha256 = "abc123",
                processPath = "C:\\Windows\\explorer.exe",
                componentName = "LocalAnalysis"
            ),
            makeAlert(
                id = "2",
                timestamp = now - 2.hours,
                severity = Severity.HIGH,
                alertType = AlertType.EDR,
                description = "Suspicious network connection",
                processPath = "C:\\Windows\\svchost.exe",
                user = "SYSTEM"
            ),
            makeAlert(
                id = "3",
                timestamp = now - 5.hours,
                severity = Severity.MEDIUM,
                alertType = AlertType.PREVENTION,
                description = "WildFire detected suspicious file",
                sha256 = "def456",
                componentName = "WildFire"
            ),
            makeAlert(
                id = "4",
                timestamp = now - 25.hours,
                severity = Severity.LOW,
                alertType = AlertType.EDR,
                description = "Registry modification",
                user = "admin"
            ),
            makeAlert(
                id = "5",
                timestamp = now - (8 * 24).hours,
                severity = Severity.INFO,
                alertType = AlertType.TELEMETRY,
                description = "Agent check-in"
            )
        )
    }

    @Test
    fun `filter by searchText matches across fields`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(searchText = "explorer"))
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `filter by searchText matches sha256`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(searchText = "abc123"))
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `filter by searchText matches user`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(searchText = "admin"))
        assertEquals(1, results.size)
        assertEquals("4", results[0].id)
    }

    @Test
    fun `filter by searchText is case insensitive`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(searchText = "LOCALANALYSIS"))
        assertEquals(1, results.size)
    }

    @Test
    fun `filter by severity`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(severities = setOf(Severity.CRITICAL, Severity.HIGH)))
        assertEquals(2, results.size)
        assertTrue(results.all { it.severity in setOf(Severity.CRITICAL, Severity.HIGH) })
    }

    @Test
    fun `filter by alertType`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(alertTypes = setOf(AlertType.PREVENTION)))
        assertEquals(2, results.size)
        assertTrue(results.all { it.alertType == AlertType.PREVENTION })
    }

    @Test
    fun `filter by timeRange LAST_1H`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(timeRange = TimeRange.LAST_1H))
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `filter by timeRange LAST_6H`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(timeRange = TimeRange.LAST_6H))
        assertEquals(3, results.size)
    }

    @Test
    fun `filter by timeRange LAST_24H`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(timeRange = TimeRange.LAST_24H))
        assertEquals(3, results.size)
    }

    @Test
    fun `filter by timeRange ALL returns everything`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(timeRange = TimeRange.ALL))
        assertEquals(5, results.size)
    }

    @Test
    fun `filter with multiple criteria applies all`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(
            alertTypes = setOf(AlertType.PREVENTION),
            severities = setOf(Severity.CRITICAL),
            timeRange = TimeRange.LAST_6H
        ))
        assertEquals(1, results.size)
        assertEquals("1", results[0].id)
    }

    @Test
    fun `filter with empty criteria returns all`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria())
        assertEquals(5, results.size)
    }

    @Test
    fun `filter with no matches returns empty`() {
        val repo = createRepoWithAlerts()

        val results = repo.filter(FilterCriteria(searchText = "nonexistent"))
        assertTrue(results.isEmpty())
    }

    // --- Helpers ---

    private fun createRepoWithAlerts(): LogRepository {
        val watcher = LogWatcher(
            scope = CoroutineScope(Dispatchers.Default),
            logDirectory = java.nio.file.Path.of("/tmp/nonexistent")
        )
        val repo = LogRepository(logWatcher = watcher)
        // Inject test alerts via reflection-free approach: use the filter on the StateFlow
        val alerts = createTestAlerts()
        val field = LogRepository::class.java.getDeclaredField("_alerts")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<List<UnifiedAlert>>
        stateFlow.value = alerts
        return repo
    }

    private fun makeAlert(
        id: String,
        timestamp: Instant = Clock.System.now(),
        severity: Severity = Severity.MEDIUM,
        alertType: AlertType = AlertType.EDR,
        description: String = "Test alert",
        sha256: String? = null,
        processPath: String? = null,
        user: String? = null,
        componentName: String? = null
    ) = UnifiedAlert(
        id = id,
        timestamp = timestamp,
        severity = severity,
        alertType = alertType,
        source = AlertSource.LOCAL_XML,
        processPath = processPath,
        commandLine = null,
        sha256 = sha256,
        md5 = null,
        filePath = null,
        user = user,
        description = description,
        actionTaken = null,
        componentName = componentName,
        rawData = null
    )
}
