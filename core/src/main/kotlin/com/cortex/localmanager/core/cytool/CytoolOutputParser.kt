package com.cortex.localmanager.core.cytool

import com.cortex.localmanager.core.models.AgentInfo
import com.cortex.localmanager.core.models.FileSearchResult
import com.cortex.localmanager.core.models.ProtectionFeature

object CytoolOutputParser {

    /**
     * Parses `cytool protect query` output:
     * ```
     * Protection      Mode            State
     * Process         Policy          Enabled
     * Registry        Policy          Enabled
     * ```
     */
    fun parseProtectQuery(output: String): List<ProtectionFeature> {
        return output.lines()
            .drop(1) // skip header row
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s{2,}".toRegex())
                if (parts.size >= 3) {
                    ProtectionFeature(
                        name = parts[0].trim(),
                        mode = parts[1].trim(),
                        enabled = parts[2].trim().equals("Enabled", ignoreCase = true)
                    )
                } else null
            }
    }

    /**
     * Parses `cytool info query` output:
     * ```
     * Content Type: 2200
     * Content Build: 33468
     * Content Version: 2200-33468
     * Event Log: 1
     * ```
     */
    fun parseInfoQuery(output: String): AgentInfo? {
        val map = output.lines()
            .filter { ":" in it }
            .associate { line ->
                val (key, value) = line.split(":", limit = 2)
                key.trim() to value.trim()
            }

        return try {
            AgentInfo(
                contentType = map["Content Type"]?.toInt() ?: return null,
                contentBuild = map["Content Build"]?.toInt() ?: return null,
                contentVersion = map["Content Version"] ?: return null,
                eventLog = map["Event Log"]?.toInt() ?: 0
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Parses `cytool file_search hash <sha256>` output.
     * Returns one result per file location block. Returns empty list if not found.
     *
     * Example output:
     * ```
     * SHA256: 03be52...
     * MD5: 2da60e17...
     * File ID: 1970324837135989
     * File Path: C:\Users\lab-user\AppData\Local\Google\...
     * Date Created: 2026-03-11T20:15:22
     * Date Last Modified: 2026-03-11T20:15:29
     * Created By User: BYOS\lab-user
     * Created By SID: S-1-5-21-567966250-...
     * ```
     */
    fun parseFileSearch(output: String): List<FileSearchResult> {
        if (output.contains("not found", ignoreCase = true) || output.isBlank()) {
            return emptyList()
        }

        // Split into blocks — each block starts with "SHA256:"
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in output.lines()) {
            if (line.trim().startsWith("SHA256:", ignoreCase = true) && current.isNotEmpty()) {
                blocks.add(current.toString())
                current.clear()
            }
            current.appendLine(line)
        }
        if (current.isNotBlank()) blocks.add(current.toString())

        return blocks.mapNotNull { block ->
            val map = block.lines()
                .filter { ":" in it }
                .associate { line ->
                    val colonIdx = line.indexOf(':')
                    val key = line.substring(0, colonIdx).trim()
                    val value = line.substring(colonIdx + 1).trim()
                    key to value
                }

            val sha256 = map["SHA256"] ?: return@mapNotNull null
            val md5 = map["MD5"] ?: return@mapNotNull null
            val filePath = map["File Path"] ?: return@mapNotNull null

            FileSearchResult(
                sha256 = sha256,
                md5 = md5,
                fileId = map["File ID"],
                filePath = filePath,
                dateCreated = map["Date Created"],
                dateLastModified = map["Date Last Modified"],
                createdByUser = map["Created By User"],
                createdBySid = map["Created By SID"]
            )
        }
    }

    /**
     * Extracts JSON array from `cytool persist print <db>` output.
     * Strips the informational preamble and "Iterated [N] entries." suffix.
     *
     * Example preamble:
     * ```
     * Database (identified by name) is in use by the agent - attempting to access the DB via RPC.
     * Enter supervisor password:
     * [{"key":"...","value":{...}}, ...]
     * Iterated [42] entries.
     * ```
     */
    fun extractJsonFromPersist(output: String): String? {
        val lines = output.lines()
        // Find the line(s) that form the JSON array
        val jsonStart = lines.indexOfFirst { it.trimStart().startsWith("[") }
        if (jsonStart == -1) return null

        val sb = StringBuilder()
        for (i in jsonStart until lines.size) {
            val line = lines[i]
            // Stop at "Iterated [N] entries." suffix
            if (line.trim().startsWith("Iterated [") || line.trim().startsWith("Iterated [")) break
            sb.appendLine(line)
        }

        val json = sb.toString().trim()
        return json.ifEmpty { null }
    }
}
