package com.cortex.localmanager.core.logs

import com.cortex.localmanager.core.models.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * Parses the JSON output from `cytool security_events print` or `cytool persist print security_events.db`.
 * This is the richest alert data source — returns full prevention event detail.
 *
 * Handles two JSON formats:
 * 1. Direct event objects (from `security_events print`): [{event fields...}, ...]
 * 2. Persist print wrapped format: [{"key":"...","value":{"event":{event fields...},"lruData":{...}}}, ...]
 *
 * Field names may be snake_case or camelCase depending on the source.
 */
object SecurityEventsParser {

    fun parseSecurityEvents(jsonArrayString: String): List<UnifiedAlert> {
        if (jsonArrayString.isBlank()) return emptyList()

        return try {
            val array = json.parseToJsonElement(jsonArrayString).jsonArray
            logger.info { "Parsing ${array.size} security events" }
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    // Unwrap persist print format: {"key":"...","value":{"event":{...},"lruData":{...}}}
                    val eventObj = obj["value"]?.jsonObject?.get("event")?.jsonObject  // persist print
                        ?: obj["value"]?.jsonObject   // value without event wrapper
                        ?: obj["event"]?.jsonObject    // event at top level
                        ?: obj                         // direct event object
                    parseEvent(eventObj)
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

        // Process info — handle both camelCase and snake_case
        val sourceProcess = obj["sourceProcess"]?.jsonObject
            ?: obj["source_process"]?.jsonObject
        val processCommandLine = sourceProcess?.str("commandLine")
            ?: sourceProcess?.str("command_line")
        val processPath = processCommandLine?.trim()?.let { extractExePath(it) }
        val pid = sourceProcess?.long("id")
        val parentPid = sourceProcess?.long("parentId")
            ?: sourceProcess?.long("parent_id")
        val userName = sourceProcess?.str("userName")
            ?: sourceProcess?.str("user_name")

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
        val quarantineId = firstFile?.str("quarantineId")
            ?: firstFile?.str("quarantine_id")
        val applicationName = firstFile?.str("name")
        val publisher = firstFile?.str("publisher")

        // Severity mapping — handle both camelCase and snake_case
        val severityStr = obj.str("severity")
        val verdictScore = obj.int("verdictScore") ?: obj.int("verdict_score")
        val severity = mapSeverity(severityStr, verdictScore, blocked)

        // Component — derive from moduleId, enriched with WildFire verdict if available
        val moduleId = obj.int("moduleId") ?: obj.int("module_id")
        val wfVerdict = obj.int("wfVerdict") ?: obj.int("wf_verdict")
        val componentName = mapComponent(moduleId, description, wfVerdict)

        // Action
        val actionTaken = when {
            quarantineId != null && quarantineId.isNotEmpty() -> "quarantined"
            else -> "blocked"
        }

        // BTP-specific: extract AMSI script content from dynamicAnalysis.internals
        val dynamicAnalysis = obj["dynamicAnalysis"]?.jsonObject
        val internals = dynamicAnalysis?.get("internals")?.jsonArray
        var scriptContent: String? = null
        var scriptEngine: String? = null
        if (internals != null) {
            for (internal in internals) {
                val internalObj = internal.jsonObject
                val name = internalObj.str("name") ?: ""
                if (name.contains("amsi", ignoreCase = true)) {
                    val attrs = internalObj["attributes"]?.jsonObject ?: continue
                    val content = attrs.str("content")
                    if (content != null && content.isNotBlank() && scriptContent == null) {
                        scriptContent = content
                        scriptEngine = attrs.str("script_engine")?.let { engine ->
                            // Extract just the engine name: "PowerShell_C:\...\powershell.exe_10..." → "PowerShell"
                            engine.substringBefore("_").ifEmpty { engine }
                        }
                    }
                }
            }
        }

        // MITRE ATT&CK
        val techniques = obj["techniqueId"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        val tactics = obj["tacticId"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        // BTP rule info
        val ruleName = obj.str("ruleName")
        val ruleDescription = obj.str("ruleExternalDescription")

        // Child processes from processes map (skip the source process)
        val childProcesses = mutableListOf<ProcessInfo>()
        val processesMap = obj["processes"]?.jsonObject
        val sourceInstanceId = sourceProcess?.str("instanceId")
        if (processesMap != null) {
            for ((_, procElement) in processesMap) {
                val proc = procElement.jsonObject
                val instId = proc.str("instanceId")
                if (instId == sourceInstanceId) continue // skip source process
                val cmdLine = proc.str("commandLine")?.trim() ?: continue
                val procName = extractExePath(cmdLine)?.substringAfterLast("\\") ?: continue
                childProcesses.add(ProcessInfo(
                    name = procName,
                    commandLine = cmdLine,
                    pid = proc.long("id"),
                    path = extractExePath(cmdLine)
                ))
            }
        }

        // Build raw data for the detail view
        val rawData = try {
            prettyJson.encodeToString(JsonObject.serializer(), obj)
        } catch (_: Exception) {
            obj.toString()
        }

        return UnifiedAlert(
            id = obj.str("preventionId") ?: obj.str("prevention_id") ?: UUID.randomUUID().toString(),
            timestamp = timestamp,
            severity = severity,
            alertType = AlertType.PREVENTION,
            source = AlertSource.SECURITY_EVENTS,
            processPath = processPath,
            commandLine = processCommandLine?.trim(),
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
            rawData = rawData,
            scriptContent = scriptContent,
            scriptEngine = scriptEngine,
            mitreTechniques = techniques,
            mitreTactics = tactics,
            ruleName = ruleName,
            ruleDescription = ruleDescription,
            childProcesses = childProcesses
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

    private fun mapComponent(moduleId: Int?, description: String?, wfVerdict: Int? = null): String {
        // Known module IDs from Cortex XDR
        val baseComponent = when (moduleId) {
            294 -> "LocalAnalysis"
            256, 327 -> "BehavioralThreat"
            280 -> "WildFire"
            else -> when {
                description?.contains("Local Analysis", ignoreCase = true) == true -> "LocalAnalysis"
                description?.contains("WildFire", ignoreCase = true) == true -> "WildFire"
                description?.contains("Behavioral", ignoreCase = true) == true -> "BehavioralThreat"
                else -> "Prevention"
            }
        }
        // If initial detection was LocalAnalysis but WildFire also returned a malicious verdict,
        // show both to indicate the enriched detection chain
        if (baseComponent == "LocalAnalysis" && wfVerdict != null && wfVerdict >= 2) {
            return "LocalAnalysis + WildFire"
        }
        return baseComponent
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
