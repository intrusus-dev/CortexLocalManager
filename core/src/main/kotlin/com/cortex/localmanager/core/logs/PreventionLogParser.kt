package com.cortex.localmanager.core.logs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.StringReader
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import org.xml.sax.InputSource

private val logger = KotlinLogging.logger {}

class PreventionLogParser {

    private val docBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        // Disable external entities for security
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }

    fun parseXmlFile(path: Path): Result<PreventionAlert> = runCatching {
        val xmlContent = path.readText()
        parseXmlString(xmlContent, path.name)
    }

    fun parseXmlString(xmlContent: String, sourceFile: String? = null): PreventionAlert {
        val doc = docBuilderFactory.newDocumentBuilder()
            .parse(InputSource(StringReader(xmlContent)))
        doc.documentElement.normalize()

        val root = doc.documentElement

        // Parse date + time into Instant
        val dateStr = getElementText(root, "Date")
        val timeStr = getElementText(root, "Time")
        val timestamp = parseDateTime(dateStr, timeStr)

        // Parse suspicious element attributes
        val suspiciousNodes = root.getElementsByTagName("Suspicious")
        val suspiciousAttrs = if (suspiciousNodes.length > 0) suspiciousNodes.item(0).attributes else null

        val pid = suspiciousAttrs?.getNamedItem("Pid")?.nodeValue?.toLongOrNull()
        val blocked = suspiciousAttrs?.getNamedItem("Block")?.nodeValue?.equals("True", ignoreCase = true) ?: false
        val mode = suspiciousAttrs?.getNamedItem("Mode")?.nodeValue

        // Parse additional arguments for hashes, paths, user info
        val additionalArgs = getElementText(root, "AdditionalArguments") ?: ""
        val sha256 = extractFromArgs(additionalArgs, "SHA256")
            ?: extractFromArgs(additionalArgs, "sha256")
        val md5 = extractFromArgs(additionalArgs, "MD5")
            ?: extractFromArgs(additionalArgs, "md5")
        val alertFilePath = extractFromArgs(additionalArgs, "FilePath")
            ?: extractFromArgs(additionalArgs, "Path")
        val userSid = extractFromArgs(additionalArgs, "SID")
            ?: extractFromArgs(additionalArgs, "UserSID")

        return PreventionAlert(
            timestamp = timestamp,
            processPath = getElementText(root, "ProcessPath")
                ?: extractFromArgs(additionalArgs, "ProcessPath"),
            pid = pid,
            blocked = blocked,
            trigger = mode,
            componentName = getElementText(root, "ComponentName"),
            operationStatus = getElementText(root, "OperationStatus"),
            description = getElementText(root, "Description"),
            sha256 = sha256,
            md5 = md5,
            filePath = alertFilePath,
            user = getElementText(root, "UserName")
                ?: extractFromArgs(additionalArgs, "User"),
            userSid = userSid,
            osVersion = getElementText(root, "OSVersion"),
            sourceFile = sourceFile
        )
    }

    fun parseDirectory(dir: Path): Result<List<PreventionAlert>> = runCatching {
        val alerts = mutableListOf<PreventionAlert>()
        dir.listDirectoryEntries("*.xml")
            .filter { it.isRegularFile() }
            .sortedByDescending { it.name }
            .forEach { xmlPath ->
                parseXmlFile(xmlPath)
                    .onSuccess { alerts.add(it) }
                    .onFailure { logger.warn(it) { "Failed to parse ${xmlPath.name}" } }
            }
        logger.info { "Loaded ${alerts.size} prevention alerts from $dir" }
        alerts
    }

    /**
     * Read prevention alerts from Windows Event Log via wevtutil.
     * Only works on Windows — returns empty list on other platforms.
     *
     * Returns a pair: (parsed PreventionAlerts, embedded JSON strings for SecurityEventsParser)
     */
    fun readFromEventLog(maxEvents: Int = 100): Result<EventLogResults> = runCatching {
        val os = System.getProperty("os.name", "").lowercase()
        if ("windows" !in os) {
            logger.debug { "Skipping Event Log read — not on Windows" }
            return@runCatching EventLogResults(emptyList(), emptyList())
        }

        // Try multiple known Cortex XDR event log channels
        val channels = listOf(
            "Palo Alto Networks",
            "Traps",
            "Cortex XDR"
        )

        val allParsedAlerts = mutableListOf<PreventionAlert>()
        val allEmbeddedJsons = mutableListOf<String>()

        for (channel in channels) {
            val results = readChannel(channel, maxEvents)
            processResults(results, allParsedAlerts, allEmbeddedJsons, channel)
        }

        // Also try querying by provider name in the Application/System logs
        val providerQuery = "*[System[Provider[@Name='Traps'] or Provider[@Name='Cortex XDR'] or Provider[@Name='CortexXDR']]]"
        for (logName in listOf("Application", "System")) {
            val results = readChannelWithQuery(logName, providerQuery, maxEvents)
            processResults(results, allParsedAlerts, allEmbeddedJsons, logName)
        }

        logger.info { "Event Log: ${allParsedAlerts.size} parsed alerts, ${allEmbeddedJsons.size} embedded JSON events" }
        EventLogResults(allParsedAlerts, allEmbeddedJsons)
    }

    /** Aggregated results from Event Log reading */
    data class EventLogResults(
        val alerts: List<PreventionAlert>,
        val embeddedJsons: List<String>
    )

    private fun processResults(
        results: List<EventLogParseResult>,
        alerts: MutableList<PreventionAlert>,
        jsons: MutableList<String>,
        sourceName: String
    ) {
        if (results.isEmpty()) return
        var alertCount = 0
        var jsonCount = 0
        for (result in results) {
            when (result) {
                is EventLogParseResult.ParsedAlert -> { alerts.add(result.alert); alertCount++ }
                is EventLogParseResult.EmbeddedJson -> { jsons.add(result.json); jsonCount++ }
            }
        }
        if (alertCount > 0 || jsonCount > 0) {
            logger.info { "Channel '$sourceName': $alertCount parsed alerts, $jsonCount embedded JSON events" }
        }
    }

    private fun readChannel(channel: String, maxEvents: Int): List<EventLogParseResult> {
        return try {
            val process = ProcessBuilder(
                "wevtutil", "qe", channel,
                "/rd:true", "/f:xml", "/c:$maxEvents"
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                logger.debug { "wevtutil channel '$channel' returned exit code $exitCode" }
                return emptyList()
            }

            parseEventLogOutput(output)
        } catch (e: Exception) {
            logger.debug { "Failed to read channel '$channel': ${e.message}" }
            emptyList()
        }
    }

    private fun readChannelWithQuery(logName: String, query: String, maxEvents: Int): List<EventLogParseResult> {
        return try {
            val process = ProcessBuilder(
                "wevtutil", "qe", logName,
                "/q:$query", "/rd:true", "/f:xml", "/c:$maxEvents"
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) return emptyList()
            parseEventLogOutput(output)
        } catch (e: Exception) {
            logger.debug { "Failed to query '$logName': ${e.message}" }
            emptyList()
        }
    }

    fun parseEventLogOutput(output: String): List<EventLogParseResult> {
        return output.split("</Event>")
            .filter { it.contains("<Event") }
            .mapNotNull { xmlBlock ->
                try {
                    val fullXml = xmlBlock.trim() + "</Event>"
                    parseEventLogXml(fullXml)
                } catch (e: Exception) {
                    logger.debug { "Failed to parse event log entry: ${e.message}" }
                    null
                }
            }
    }

    /**
     * Parses a single Windows Event Log XML entry.
     * Cortex XDR may embed full JSON payloads in event data, or use named Data elements.
     * Returns the embedded JSON string if found (for richer parsing via SecurityEventsParser),
     * or falls back to extracting named Data fields.
     */
    fun parseEventLogXml(xml: String): EventLogParseResult? {
        val doc = docBuilderFactory.newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val root = doc.documentElement
        val systemNode = root.getElementsByTagName("System")
        val eventDataNode = root.getElementsByTagName("EventData")

        if (systemNode.length == 0) return null

        val timeCreated = root.getElementsByTagName("TimeCreated")
        val timestampStr = if (timeCreated.length > 0) {
            timeCreated.item(0).attributes?.getNamedItem("SystemTime")?.nodeValue
        } else null

        val timestamp = timestampStr?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        } ?: return null

        // Collect all data from EventData — both named and unnamed elements
        val dataMap = mutableMapOf<String, String>()
        val unnamedData = mutableListOf<String>()
        if (eventDataNode.length > 0) {
            val dataNodes = eventDataNode.item(0).childNodes
            for (i in 0 until dataNodes.length) {
                val node = dataNodes.item(i)
                val text = node.textContent?.trim() ?: continue
                if (text.isEmpty()) continue
                val name = node.attributes?.getNamedItem("Name")?.nodeValue
                if (name != null) {
                    dataMap[name] = text
                } else if (text.isNotBlank()) {
                    unnamedData.add(text)
                }
            }
        }

        // Check if any data field contains embedded JSON (Cortex XDR pattern)
        val allTextValues = dataMap.values + unnamedData
        for (value in allTextValues) {
            val trimmed = value.trim()
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                logger.debug { "Found embedded JSON in Event Log entry" }
                return EventLogParseResult.EmbeddedJson(trimmed, timestamp)
            }
        }

        // Also check the full text content of EventData as fallback
        if (eventDataNode.length > 0) {
            val fullText = eventDataNode.item(0).textContent?.trim() ?: ""
            if (fullText.startsWith("{") || fullText.startsWith("[")) {
                logger.debug { "Found embedded JSON in EventData full text" }
                return EventLogParseResult.EmbeddedJson(fullText, timestamp)
            }
        }

        // Fall back to named Data elements
        val alert = PreventionAlert(
            timestamp = timestamp,
            processPath = dataMap["ProcessPath"],
            pid = dataMap["PID"]?.toLongOrNull(),
            blocked = dataMap["OperationStatus"]?.equals("blocked", ignoreCase = true) ?: false,
            trigger = dataMap["Mode"],
            componentName = dataMap["ComponentName"],
            operationStatus = dataMap["OperationStatus"],
            description = dataMap["Description"],
            sha256 = dataMap["SHA256"],
            md5 = dataMap["MD5"],
            filePath = dataMap["FilePath"],
            user = dataMap["UserName"],
            userSid = dataMap["UserSID"],
            osVersion = null,
            sourceFile = "WindowsEventLog"
        )
        return EventLogParseResult.ParsedAlert(alert)
    }

    /** Result of parsing an Event Log XML entry */
    sealed class EventLogParseResult {
        /** Cortex XDR embeds full JSON in the event data — parse with SecurityEventsParser */
        data class EmbeddedJson(val json: String, val timestamp: Instant) : EventLogParseResult()
        /** Traditional named Data elements — already parsed into a PreventionAlert */
        data class ParsedAlert(val alert: PreventionAlert) : EventLogParseResult()
    }

    private fun getElementText(root: org.w3c.dom.Element, tagName: String): String? {
        val nodes = root.getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim()?.ifEmpty { null } else null
    }

    private fun extractFromArgs(args: String, key: String): String? {
        // Matches patterns like "SHA256=abc123" or "SHA256: abc123"
        val regex = Regex("""(?i)$key\s*[=:]\s*(\S+)""")
        return regex.find(args)?.groupValues?.get(1)
    }

    companion object {
        private val DATE_PATTERN = Regex(
            """(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s+(\w+)\s+(\d+),\s+(\d{4})"""
        )
        private val MONTH_MAP = mapOf(
            "January" to 1, "February" to 2, "March" to 3, "April" to 4,
            "May" to 5, "June" to 6, "July" to 7, "August" to 8,
            "September" to 9, "October" to 10, "November" to 11, "December" to 12
        )

        fun parseDateTime(dateStr: String?, timeStr: String?): Instant {
            if (dateStr == null || timeStr == null) return Instant.DISTANT_PAST

            val match = DATE_PATTERN.find(dateStr) ?: return Instant.DISTANT_PAST
            val month = MONTH_MAP[match.groupValues[1]] ?: return Instant.DISTANT_PAST
            val day = match.groupValues[2].toIntOrNull() ?: return Instant.DISTANT_PAST
            val year = match.groupValues[3].toIntOrNull() ?: return Instant.DISTANT_PAST

            val timeParts = timeStr.split(":")
            if (timeParts.size < 3) return Instant.DISTANT_PAST
            val hour = timeParts[0].toIntOrNull() ?: return Instant.DISTANT_PAST
            val minute = timeParts[1].toIntOrNull() ?: return Instant.DISTANT_PAST
            val second = timeParts[2].toIntOrNull() ?: return Instant.DISTANT_PAST

            return try {
                LocalDateTime(year, month, day, hour, minute, second)
                    .toInstant(TimeZone.currentSystemDefault())
            } catch (_: Exception) {
                Instant.DISTANT_PAST
            }
        }
    }
}
