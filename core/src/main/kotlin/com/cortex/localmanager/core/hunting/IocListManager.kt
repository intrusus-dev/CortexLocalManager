package com.cortex.localmanager.core.hunting

import com.cortex.localmanager.core.models.FileSearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = KotlinLogging.logger {}
private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

data class IocEntry(
    val sha256: String,
    val description: String = "",
    val source: String = "manual"
)

data class IocSearchResult(
    val ioc: IocEntry,
    val found: Boolean,
    val locations: List<FileSearchResult> = emptyList()
)

enum class ExportFormat { JSON, CSV }

class IocListManager {

    fun validateHash(hash: String): Boolean = SHA256_REGEX.matches(hash.trim())

    fun parseFile(path: Path): Result<List<IocEntry>> = when (path.extension.lowercase()) {
        "json" -> parseJsonFile(path)
        "csv" -> parseCsvFile(path)
        else -> parseTextFile(path)
    }

    fun parseTextFile(path: Path): Result<List<IocEntry>> = runCatching {
        path.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                // Line could be just a hash, or "hash,description"
                val parts = line.split(",", limit = 2)
                val hash = parts[0].trim()
                if (validateHash(hash)) {
                    IocEntry(
                        sha256 = hash.lowercase(),
                        description = parts.getOrNull(1)?.trim() ?: "",
                        source = "imported"
                    )
                } else {
                    logger.debug { "Skipping invalid hash line: $line" }
                    null
                }
            }
    }

    fun parseCsvFile(path: Path): Result<List<IocEntry>> = runCatching {
        val lines = path.readLines().filter { it.isNotBlank() }
        // Skip header if present
        val dataLines = if (lines.firstOrNull()?.lowercase()?.contains("sha256") == true) {
            lines.drop(1)
        } else lines

        dataLines.mapNotNull { line ->
            val parts = line.split(",").map { it.trim().trim('"') }
            val hash = parts[0]
            if (validateHash(hash)) {
                IocEntry(
                    sha256 = hash.lowercase(),
                    description = parts.getOrNull(1) ?: "",
                    source = parts.getOrNull(2) ?: "imported"
                )
            } else null
        }
    }

    fun parseJsonFile(path: Path): Result<List<IocEntry>> = runCatching {
        val element = json.parseToJsonElement(path.readText())
        val array = if (element is JsonArray) element else JsonArray(listOf(element))

        array.mapNotNull { item ->
            val obj = item.jsonObject
            val hash = obj["sha256"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (validateHash(hash)) {
                IocEntry(
                    sha256 = hash.lowercase(),
                    description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                    source = obj["source"]?.jsonPrimitive?.contentOrNull ?: "imported"
                )
            } else null
        }
    }

    fun exportResults(results: List<IocSearchResult>, outputPath: Path, format: ExportFormat): Result<Path> = runCatching {
        when (format) {
            ExportFormat.JSON -> {
                val sb = StringBuilder("[\n")
                results.forEachIndexed { i, r ->
                    sb.append("  {\n")
                    sb.append("    \"sha256\": \"${r.ioc.sha256}\",\n")
                    sb.append("    \"description\": \"${r.ioc.description.replace("\"", "\\\"")}\",\n")
                    sb.append("    \"found\": ${r.found},\n")
                    sb.append("    \"locationCount\": ${r.locations.size}")
                    if (r.locations.isNotEmpty()) {
                        sb.append(",\n    \"locations\": [\n")
                        r.locations.forEachIndexed { j, loc ->
                            sb.append("      {\"path\": \"${loc.filePath.replace("\\", "\\\\")}\", \"sha256\": \"${loc.sha256}\"}")
                            if (j < r.locations.size - 1) sb.append(",")
                            sb.append("\n")
                        }
                        sb.append("    ]")
                    }
                    sb.append("\n  }")
                    if (i < results.size - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append("]")
                outputPath.writeText(sb.toString())
            }
            ExportFormat.CSV -> {
                val header = "SHA256,Description,Found,LocationCount,FilePaths"
                val rows = results.joinToString("\n") { r ->
                    val paths = r.locations.joinToString(";") { it.filePath }
                    "${r.ioc.sha256},\"${r.ioc.description}\",${r.found},${r.locations.size},\"$paths\""
                }
                outputPath.writeText("$header\n$rows")
            }
        }
        outputPath
    }
}
