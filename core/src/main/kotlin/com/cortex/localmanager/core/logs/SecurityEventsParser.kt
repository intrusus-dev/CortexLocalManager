package com.cortex.localmanager.core.logs

import com.cortex.localmanager.core.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Parses the JSON output from `cytool security_events print`.
 * This is the richest alert data source — returns full prevention event detail.
 */
object SecurityEventsParser {

    fun parseSecurityEvents(jsonArrayString: String): List<UnifiedAlert> {
        if (jsonArrayString.isBlank()) return emptyList()

        return try {
            val array = json.parseToJsonElement(jsonArrayString).jsonArray
            logger.info { "Parsing ${array.size} security events" }
            array.mapNotNull { element ->
                try {
                    parseEvent(element.jsonObject)
                } catch (e: Exception) {
                    logger.debug { "Failed to parse security event: ${e.message}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse security events JSON" }
            emptyList()
        }
    }

    private fun parseEvent(obj: JsonObject): UnifiedAlert {
        val timestamp = obj.str("time")?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        } ?: kotlinx.datetime.Clock.System.now()

        val sha256 = obj.str("sha256")
        val md5 = obj.str("md5")
        val description = obj.str("description") ?: "Prevention Alert"
        val blocked = obj.bool("blocked") ?: false

        // Process info from source_process
        val sourceProcess = obj["source_process"]?.jsonObject
        val processCommandLine = sourceProcess?.str("command_line")?.trim()
        val processPath = processCommandLine?.let { extractExePath(it) }
        val pid = sourceProcess?.long("id")
        val parentPid = sourceProcess?.long("parent_id")
        val userName = sourceProcess?.str("user_name")

        // User info
        val user = obj["user"]?.jsonObject
        val fullUserName = user?.str("name")
        val userSid = user?.str("sid")

        // File info from files array
        val files = obj["files"]?.jsonArray
        val firstFile = files?.firstOrNull()?.jsonObject
        val filePath = firstFile?.str("path")
        val fileSha256 = firstFile?.str("sha256") ?: sha256
        val fileMd5 = firstFile?.str("md5") ?: md5
        val fileSize = firstFile?.str("size")?.toLongOrNull()
        val quarantineId = firstFile?.str("quarantine_id")
        val applicationName = firstFile?.str("name")
        val publisher = firstFile?.str("publisher")

        // Severity mapping
        val severityStr = obj.str("severity")
        val verdictScore = obj.int("verdict_score")
        val severity = mapSeverity(severityStr, verdictScore, blocked)

        // Component — derive from module_id or description
        val moduleId = obj.int("module_id")
        val componentName = mapComponent(moduleId, description)

        // Action
        val actionTaken = when {
            blocked -> "blocked"
            quarantineId != null -> "quarantined"
            else -> "reported"
        }

        // Build raw data for the detail view
        val rawData = try {
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), obj)
        } catch (_: Exception) {
            obj.toString()
        }

        return UnifiedAlert(
            id = obj.str("prevention_id") ?: UUID.randomUUID().toString(),
            timestamp = timestamp,
            severity = severity,
            alertType = AlertType.PREVENTION,
            source = AlertSource.SECURITY_EVENTS,
            processPath = processPath,
            commandLine = processCommandLine,
            pid = pid,
            parentPid = parentPid,
            sha256 = fileSha256,
            md5 = fileMd5,
            filePath = filePath,
            fileSize = fileSize,
            user = fullUserName ?: userName,
            userSid = userSid,
            description = description,
            actionTaken = actionTaken,
            componentName = componentName,
            applicationName = applicationName,
            publisher = publisher,
            rawData = rawData
        )
    }

    private fun mapSeverity(severityStr: String?, verdictScore: Int?, blocked: Boolean): Severity {
        // Use verdict score if available
        if (verdictScore != null) {
            return when {
                verdictScore >= 80 -> Severity.CRITICAL
                verdictScore >= 60 -> Severity.HIGH
                verdictScore >= 40 -> Severity.MEDIUM
                verdictScore >= 20 -> Severity.LOW
                else -> Severity.INFO
            }
        }
        return when (severityStr?.lowercase()) {
            "critical", "kcritical" -> Severity.CRITICAL
            "high", "khigh" -> Severity.HIGH
            "medium", "kmedium" -> Severity.MEDIUM
            "low", "klow" -> Severity.LOW
            else -> if (blocked) Severity.HIGH else Severity.MEDIUM
        }
    }

    private fun mapComponent(moduleId: Int?, description: String?): String {
        // Known module IDs from Cortex XDR
        return when (moduleId) {
            294 -> "LocalAnalysis"
            256 -> "BehavioralThreat"
            280 -> "WildFire"
            else -> when {
                description?.contains("Local Analysis", ignoreCase = true) == true -> "LocalAnalysis"
                description?.contains("WildFire", ignoreCase = true) == true -> "WildFire"
                description?.contains("Behavioral", ignoreCase = true) == true -> "BehavioralThreat"
                else -> "Prevention"
            }
        }
    }

    private fun extractExePath(commandLine: String): String? {
        // Extract exe path from command line like: "C:\path\to\file.exe" --args
        val trimmed = commandLine.trim()
        return if (trimmed.startsWith("\"")) {
            trimmed.substringAfter("\"").substringBefore("\"")
        } else {
            trimmed.split(" ").firstOrNull()
        }
    }
}

// JSON helper extensions
private fun JsonObject.str(key: String): String? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

private fun JsonObject.int(key: String): Int? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.intOrNull }

private fun JsonObject.long(key: String): Long? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.longOrNull }

private fun JsonObject.bool(key: String): Boolean? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.booleanOrNull }
