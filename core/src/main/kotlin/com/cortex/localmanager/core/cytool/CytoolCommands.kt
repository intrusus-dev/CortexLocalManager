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

    suspend fun lastCheckin(): CytoolResult<String?> {
        return when (val result = executor.execute("last_checkin")) {
            is CytoolResult.Success -> {
                val timestamp = result.data.lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                CytoolResult.Success(timestamp, result.rawOutput)
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

    // --- Network ---

    suspend fun checkin(): CytoolResult<Unit> {
        return when (val result = executor.execute("checkin")) {
            is CytoolResult.Success -> CytoolResult.Success(Unit, result.rawOutput)
            is CytoolResult.Error -> result
            is CytoolResult.Timeout -> result
        }
    }
}
