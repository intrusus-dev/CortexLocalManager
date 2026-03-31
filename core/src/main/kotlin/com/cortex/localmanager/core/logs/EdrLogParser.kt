package com.cortex.localmanager.core.logs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class EdrLogParser {

    fun parseTarArchive(tarPath: Path): Result<List<EdrEvent>> = runCatching {
        val events = mutableListOf<EdrEvent>()
        TarArchiveInputStream(BufferedInputStream(FileInputStream(tarPath.toFile()))).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = tar.readBytes().decodeToString()
                    val suffix = entry.name.substringAfterLast("-", "")
                    val parsed = parseJsonContent(content, suffix, entry.name)
                    events.addAll(parsed)
                }
                entry = tar.nextEntry
            }
        }
        logger.debug { "Parsed ${events.size} events from ${tarPath.name}" }
        events
    }

    fun parseJsonFile(jsonPath: Path, suffix: String): Result<List<EdrEvent>> = runCatching {
        val content = jsonPath.toFile().readText()
        parseJsonContent(content, suffix, jsonPath.name)
    }

    fun parseDirectory(dir: Path): Result<List<EdrEvent>> = runCatching {
        val allEvents = mutableListOf<EdrEvent>()
        dir.listDirectoryEntries("edr-*.tar")
            .filter { it.isRegularFile() }
            .sortedByDescending { it.name }
            .forEach { tarPath ->
                parseTarArchive(tarPath)
                    .onSuccess { allEvents.addAll(it) }
                    .onFailure { logger.warn(it) { "Failed to parse ${tarPath.name}" } }
            }
        logger.info { "Loaded ${allEvents.size} EDR events from ${dir}" }
        allEvents
    }

    private fun parseJsonContent(content: String, suffix: String, fileName: String): List<EdrEvent> {
        if (content.isBlank()) return emptyList()

        return try {
            val element = json.parseToJsonElement(content)
            when (element) {
                is JsonArray -> element.mapNotNull { extractEvent(it.jsonObject, suffix, fileName) }
                is JsonObject -> listOfNotNull(extractEvent(element, suffix, fileName))
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.debug { "Could not parse JSON in $fileName: ${e.message}" }
            emptyList()
        }
    }

    private fun extractEvent(obj: JsonObject, suffix: String, fileName: String): EdrEvent? {
        return try {
            EdrEvent(
                timestamp = obj.stringOrNull("timestamp")
                    ?: obj.stringOrNull("EVENT_TIMESTAMP")
                    ?: obj.stringOrNull("time"),
                eventType = obj.stringOrNull("event_type")
                    ?: obj.stringOrNull("EVENT_TYPE")
                    ?: suffix,
                processPath = obj.stringOrNull("process_image_path")
                    ?: obj.stringOrNull("ACTION_PROCESS_IMAGE_PATH")
                    ?: obj.stringOrNull("process_path"),
                commandLine = obj.stringOrNull("process_command_line")
                    ?: obj.stringOrNull("ACTION_PROCESS_COMMAND_LINE"),
                pid = obj.longOrNull("PID")
                    ?: obj.longOrNull("pid")
                    ?: obj.longOrNull("process_id"),
                parentPid = obj.longOrNull("PARENT_PID")
                    ?: obj.longOrNull("parent_pid"),
                user = obj.stringOrNull("user_name")
                    ?: obj.stringOrNull("USER_NAME"),
                sha256 = obj.stringOrNull("sha256")
                    ?: obj.stringOrNull("ACTION_FILE_SHA256"),
                filePath = obj.stringOrNull("file_path")
                    ?: obj.stringOrNull("ACTION_FILE_PATH"),
                registryKey = obj.stringOrNull("registry_key")
                    ?: obj.stringOrNull("ACTION_REGISTRY_KEY_NAME"),
                networkDestination = obj.stringOrNull("dest_ip")
                    ?: obj.stringOrNull("ACTION_REMOTE_IP"),
                severity = obj.stringOrNull("severity")
                    ?: obj.stringOrNull("SEVERITY"),
                description = obj.stringOrNull("description")
                    ?: obj.stringOrNull("event_sub_type"),
                sourceFile = fileName,
                rawData = obj
            )
        } catch (e: Exception) {
            logger.debug { "Failed to extract event from $fileName: ${e.message}" }
            null
        }
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

private fun JsonObject.longOrNull(key: String): Long? =
    this[key]?.let {
        if (it is JsonNull) null
        else it.jsonPrimitive.longOrNull
    }
