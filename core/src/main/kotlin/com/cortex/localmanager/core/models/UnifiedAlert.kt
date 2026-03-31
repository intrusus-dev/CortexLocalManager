package com.cortex.localmanager.core.models

import kotlinx.datetime.Instant

data class UnifiedAlert(
    val id: String,
    val timestamp: Instant,
    val severity: Severity,
    val alertType: AlertType,
    val source: AlertSource,
    val processPath: String?,
    val commandLine: String?,
    val pid: Long? = null,
    val parentPid: Long? = null,
    val sha256: String?,
    val md5: String?,
    val filePath: String?,
    val fileSize: Long? = null,
    val user: String?,
    val userSid: String? = null,
    val description: String,
    val actionTaken: String?,
    val componentName: String?,
    val applicationName: String? = null,
    val publisher: String? = null,
    val rawData: String?
)

enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

enum class AlertType { PREVENTION, EDR, TELEMETRY }

enum class AlertSource { LOCAL_XML, EDR_JSON, EVENT_LOG, SECURITY_EVENTS }

data class FilterCriteria(
    val searchText: String? = null,
    val severities: Set<Severity>? = null,
    val alertTypes: Set<AlertType>? = null,
    val timeRange: TimeRange? = null
)

enum class TimeRange { LAST_1H, LAST_6H, LAST_24H, LAST_7D, LAST_30D, ALL }
