package com.cortex.localmanager.core.cytool

import com.cortex.localmanager.core.models.AgentInfo
import com.cortex.localmanager.core.models.FileSearchResult
import com.cortex.localmanager.core.models.ProtectionFeature

class CytoolCommands(
    private val executor: CytoolExecutor,
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
        return when (val result = executor.execute("file_search", "hash", sha256)) {
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

    // --- Network ---

    suspend fun checkin(): CytoolResult<Unit> {
        return when (val result = executor.execute("checkin")) {
            is CytoolResult.Success -> CytoolResult.Success(Unit, result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }
}
