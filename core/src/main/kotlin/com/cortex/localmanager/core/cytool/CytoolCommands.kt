package com.cortex.localmanager.core.cytool

import com.cortex.localmanager.core.models.AgentInfo
import com.cortex.localmanager.core.models.FileSearchResult
import com.cortex.localmanager.core.models.ProtectionFeature
import com.cortex.localmanager.core.models.QuarantineEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

class CytoolCommands(
    val executor: CytoolExecutor,
    private val passwordProvider: () -> String?
) {

    // --- Agent Info (no password needed) ---

    suspend fun queryInfo(): CytoolResult<AgentInfo> {
        return when (val result = executor.execute("info", "query")) {
            is CytoolResult.Success -> {
                val info = CytoolOutputParser.parseInfoQuery(result.data)
                if (info != null) {
                    CytoolResult.Success(info, result.rawOutput)
                } else {
                    CytoolResult.Error("Failed to parse agent info output", result.rawOutput, 0)
                }
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun queryProtection(): CytoolResult<List<ProtectionFeature>> {
        return when (val result = executor.execute("protect", "query")) {
            is CytoolResult.Success -> {
                val features = CytoolOutputParser.parseProtectQuery(result.data)
                CytoolResult.Success(features, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    /**
     * Returns a Pair of (display text, extractable timestamp or null).
     */
    suspend fun lastCheckin(): CytoolResult<Pair<String, String?>> {
        return when (val result = executor.execute("last_checkin")) {
            is CytoolResult.Success -> {
                val parsed = CytoolOutputParser.parseLastCheckin(result.data)
                CytoolResult.Success(parsed, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Threat Hunting ---

    suspend fun searchFileHash(sha256: String): CytoolResult<List<FileSearchResult>> {
        val password = passwordProvider()
        return when (val result = executor.execute("file_search", "hash", sha256, password = password)) {
            is CytoolResult.Success -> {
                val results = CytoolOutputParser.parseFileSearch(result.data)
                CytoolResult.Success(results, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun searchFilePath(path: String): CytoolResult<List<FileSearchResult>> {
        val password = passwordProvider()
        return when (val result = executor.execute("file_search", "path", path, password = password)) {
            is CytoolResult.Success -> {
                val results = CytoolOutputParser.parseFileSearch(result.data)
                CytoolResult.Success(results, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun startFileSystemScan(): CytoolResult<Unit> {
        return when (val result = executor.execute("file_system_scan", "start")) {
            is CytoolResult.Success -> CytoolResult.Success(Unit, result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun queryFileSystemScan(): CytoolResult<String> {
        return when (val result = executor.execute("file_system_scan", "query")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Persist Database (password required) ---

    suspend fun listDatabases(): CytoolResult<List<String>> {
        return when (val result = executor.execute("persist", "list")) {
            is CytoolResult.Success -> {
                val dbs = result.data.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.endsWith(".db") }
                CytoolResult.Success(dbs, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun printDatabase(dbName: String): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return when (val result = executor.execute("persist", "print", dbName, password = password)) {
            is CytoolResult.Success -> {
                val json = CytoolOutputParser.extractJsonFromPersist(result.data)
                if (json != null) {
                    CytoolResult.Success(json, result.rawOutput)
                } else {
                    CytoolResult.Error("No JSON data found in persist output", result.rawOutput, 0)
                }
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Security Events (primary alert source) ---

    /**
     * Fetches security events via `cytool security_events print`.
     * Returns raw JSON string. Requires supervisor password.
     */
    suspend fun getSecurityEvents(): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return when (val result = executor.execute("security_events", "print", password = password, timeoutSeconds = 60)) {
            is CytoolResult.Success -> {
                // The output is JSON directly (may have preamble text before it)
                val output = result.data
                val jsonStart = output.indexOfFirst { it == '{' || it == '[' }
                if (jsonStart >= 0) {
                    val jsonStr = output.substring(jsonStart).trim()
                    // If it's a single object (not array), wrap it
                    val normalized = if (jsonStr.startsWith("{")) "[$jsonStr]" else jsonStr
                    CytoolResult.Success(normalized, result.rawOutput)
                } else {
                    CytoolResult.Error("No JSON in security_events output", result.rawOutput, 0)
                }
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Password Validation ---

    /**
     * Validates the supervisor password by attempting a password-protected command.
     * Uses `persist list` which requires a password — if it succeeds, the password is correct.
     */
    suspend fun validatePassword(password: String): Boolean {
        return when (executor.execute("persist", "print", "agent_actions.db", password = password)) {
            is CytoolResult.Success -> true
            is CytoolResult.Error -> false
            is CytoolResult.Timeout -> false
        }
    }

    // --- Quarantine ---

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * List all quarantined files via `cytool quarantine list`.
     * Requires supervisor password. Returns JSON in persist print format.
     */
    suspend fun quarantineList(): CytoolResult<List<QuarantineEntry>> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return when (val result = executor.execute("quarantine", "list", password = password, timeoutSeconds = 60)) {
            is CytoolResult.Success -> {
                val entries = parseQuarantineOutput(result.data)
                CytoolResult.Success(entries, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    /**
     * Restore a quarantined file to its original location.
     */
    suspend fun quarantineRestore(quarantineId: String, customPath: String? = null): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        val args = mutableListOf("quarantine", "restore", quarantineId)
        if (customPath != null) args.add(customPath)

        return when (val result = executor.execute(*args.toTypedArray(), password = password)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    /**
     * Permanently delete a quarantined file.
     */
    suspend fun quarantineDelete(quarantineId: String): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return when (val result = executor.execute("quarantine", "delete", quarantineId, password = password)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    private fun parseQuarantineOutput(output: String): List<QuarantineEntry> {
        // Extract JSON array from output (may have preamble)
        val jsonStart = output.indexOfFirst { it == '[' }
        if (jsonStart < 0) return emptyList()
        var jsonStr = output.substring(jsonStart).trim()
        // Remove "Iterated [N] entries." suffix if present
        val iteratedIdx = jsonStr.lastIndexOf("Iterated [")
        if (iteratedIdx > 0) jsonStr = jsonStr.substring(0, iteratedIdx).trim()

        return try {
            val array = jsonParser.parseToJsonElement(jsonStr).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val value = obj["value"]?.jsonObject ?: return@mapNotNull null

                    val tsStr = value["timestamp"]?.jsonPrimitive?.contentOrNull
                    val timestamp = tsStr?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
                        ?: kotlinx.datetime.Clock.System.now()

                    QuarantineEntry(
                        quarantineId = key,
                        filePath = value["filePath"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                        backupPath = value["backupPath"]?.jsonPrimitive?.contentOrNull ?: "",
                        sha256 = value["sha256"]?.jsonPrimitive?.contentOrNull ?: "",
                        size = value["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
                        actionId = value["actionId"]?.jsonPrimitive?.contentOrNull ?: "",
                        timestamp = timestamp,
                        status = value["status"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                        applicationName = value["applicationName"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- Scan Operations ---

    suspend fun scanStart(): CytoolResult<String> {
        return when (val result = executor.execute("scan", "start")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun scanStop(): CytoolResult<String> {
        return when (val result = executor.execute("scan", "stop")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun scanQuery(): CytoolResult<String> {
        return when (val result = executor.execute("scan", "query")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun scanLastScanTime(): CytoolResult<String> {
        return when (val result = executor.execute("scan", "last_scan_time")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun stopFileSystemScan(): CytoolResult<String> {
        return when (val result = executor.execute("file_system_scan", "stop")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun unifiedScanStart(type: String): CytoolResult<String> {
        return when (val result = executor.execute("unified_scan", type, "start")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun unifiedScanStop(type: String): CytoolResult<String> {
        return when (val result = executor.execute("unified_scan", type, "stop")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun unifiedScanQuery(type: String): CytoolResult<String> {
        return when (val result = executor.execute("unified_scan", type, "query")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun unifiedScanPause(type: String): CytoolResult<String> {
        return when (val result = executor.execute("unified_scan", type, "pause")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun unifiedScanResume(type: String): CytoolResult<String> {
        return when (val result = executor.execute("unified_scan", type, "resume")) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Protection (Tamper Protection) ---

    /**
     * Enable/disable/policy a specific protection feature or all.
     * Actions: "enable", "disable", "policy"
     * Features: "process", "registry", "file", "service", or null for all.
     */
    suspend fun protectAction(action: String, feature: String? = null): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        val args = mutableListOf("protect", action)
        if (feature != null) args.add(feature)

        return when (val result = executor.execute(*args.toTypedArray(), password = password)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Runtime (Agent Components) ---

    data class RuntimeComponent(val name: String, val state: String)

    suspend fun runtimeQuery(): CytoolResult<List<RuntimeComponent>> {
        return when (val result = executor.execute("runtime", "query")) {
            is CytoolResult.Success -> {
                val components = result.data.lines()
                    .drop(1) // skip header "Service         State"
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.size >= 2) RuntimeComponent(parts[0], parts[1]) else null
                    }
                CytoolResult.Success(components, result.rawOutput)
            }
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun runtimeStop(component: String? = null): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        val args = if (component != null) arrayOf("runtime", "stop", component) else arrayOf("runtime", "stop")
        return when (val result = executor.execute(*args, password = password, timeoutSeconds = 60)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    suspend fun runtimeStart(component: String? = null): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        val args = if (component != null) arrayOf("runtime", "start", component) else arrayOf("runtime", "start")
        return when (val result = executor.execute(*args, password = password, timeoutSeconds = 60)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    // --- Persist Import (IoC Blacklisting) ---

    /**
     * Import a JSON file into a persist database.
     * Used for hash_overrides.db (verdict overrides / IoC blacklisting).
     */
    suspend fun persistImport(dbName: String, jsonFilePath: String): CytoolResult<String> {
        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return when (val result = executor.execute("persist", "import", dbName, jsonFilePath, password = password, timeoutSeconds = 60)) {
            is CytoolResult.Success -> CytoolResult.Success(result.data.trim(), result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }

    /**
     * Blacklist hashes by importing them into hash_overrides.db with verdict=2 (malicious).
     * Creates a temp JSON file, imports it, then cleans up.
     */
    suspend fun blacklistHashes(sha256Hashes: List<String>): CytoolResult<String> {
        if (sha256Hashes.isEmpty()) return CytoolResult.Error("No hashes to blacklist", "", -1)

        val password = passwordProvider()
            ?: return CytoolResult.Error("Supervisor password not set", "", -1)

        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            // First export current overrides to preserve them
            val currentOverrides = when (val r = executor.execute("persist", "print", "hash_overrides.db", password = password, timeoutSeconds = 60)) {
                is CytoolResult.Success -> {
                    val json = CytoolOutputParser.extractJsonFromPersist(r.data)
                    json ?: "[]"
                }
                else -> "[]"
            }

            // Parse existing entries
            val existingKeys = try {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(currentOverrides).jsonArray
                arr.mapNotNull { it.jsonObject["key"]?.jsonPrimitive?.contentOrNull }.toSet()
            } catch (_: Exception) { emptySet() }

            // Build new entries (skip duplicates)
            val newHashes = sha256Hashes.filter { it.lowercase() !in existingKeys.map { k -> k.lowercase() } }
            if (newHashes.isEmpty()) return@withContext CytoolResult.Success("All ${sha256Hashes.size} hashes already in overrides", "")

            // Build JSON array with existing + new
            val sb = StringBuilder("[\n")
            // Re-add existing
            if (currentOverrides.trim() != "[]") {
                val trimmed = currentOverrides.trim().removePrefix("[").removeSuffix("]").trim()
                if (trimmed.isNotBlank()) {
                    sb.append(trimmed)
                    sb.append(",\n")
                }
            }
            // Add new hashes
            newHashes.forEachIndexed { index, hash ->
                sb.append("""{ "key": "${hash.lowercase()}", "value": { "verdict": 2 } }""")
                if (index < newHashes.size - 1) sb.append(",\n")
            }
            sb.append("\n]")

            // Write to temp file
            val tempFile = java.io.File.createTempFile("clm_blacklist_", ".json")
            try {
                tempFile.writeText(sb.toString())

                // Import
                when (val result = executor.execute("persist", "import", "hash_overrides.db", tempFile.absolutePath, password = password, timeoutSeconds = 60)) {
                    is CytoolResult.Success -> CytoolResult.Success(
                        "Blacklisted ${newHashes.size} hash${if (newHashes.size > 1) "es" else ""}" +
                            if (sha256Hashes.size > newHashes.size) " (${sha256Hashes.size - newHashes.size} already present)" else "",
                        result.rawOutput
                    )
                    is CytoolResult.Error -> result
                    is CytoolResult.Timeout -> result
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    // --- Network ---

    suspend fun checkin(): CytoolResult<Unit> {
        return when (val result = executor.execute("checkin")) {
            is CytoolResult.Success -> CytoolResult.Success(Unit, result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }
}
