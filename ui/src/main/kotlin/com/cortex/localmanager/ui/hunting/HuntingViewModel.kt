package com.cortex.localmanager.ui.hunting

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.hunting.ExportFormat
import com.cortex.localmanager.core.hunting.IocEntry
import com.cortex.localmanager.core.hunting.IocListManager
import com.cortex.localmanager.core.hunting.IocSearchResult
import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.models.FileIdHashEntry
import com.cortex.localmanager.core.models.FileSearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

sealed class SearchOutcome {
    data object Idle : SearchOutcome()
    data object Searching : SearchOutcome()
    data class Found(
        val results: List<FileSearchResult>,
        val isQuarantined: Boolean = false,
        val quarantinePath: String? = null,
        val source: String = "file_search"  // "file_search", "alert", "quarantine"
    ) : SearchOutcome()
    data object NotFound : SearchOutcome()
    data class Error(val message: String) : SearchOutcome()
}

enum class SearchMode { HASH, PATH }

data class HuntingState(
    val searchMode: SearchMode = SearchMode.HASH,
    val searchInput: String = "",
    val inputValid: Boolean = true,
    val searchResult: SearchOutcome = SearchOutcome.Idle,
    val scanInProgress: Boolean = false,
    val scanStatus: String? = null,
    val hashEntries: List<FileIdHashEntry> = emptyList(),
    val hashEntriesLoading: Boolean = false,
    val hashEntriesFilter: String = "",
    // Blocklist
    val blocklistEntries: List<BlocklistEntry> = emptyList(),
    val blocklistLoading: Boolean = false,
    // IoC
    val iocList: List<IocEntry> = emptyList(),
    val iocSearchResults: List<IocSearchResult> = emptyList(),
    val iocBatchSearching: Boolean = false,
    val iocBatchProgress: Pair<Int, Int>? = null,
    val message: String? = null,
    val error: String? = null
)

data class BlocklistEntry(val sha256: String, val verdict: Int)

private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class HuntingViewModel(
    private val cytoolCommands: CytoolCommands,
    private val scope: CoroutineScope,
    private val logRepository: LogRepository? = null
) {
    private val _state = MutableStateFlow(HuntingState())
    val state: StateFlow<HuntingState> = _state.asStateFlow()
    private val iocManager = IocListManager()

    private var pollJob: Job? = null

    fun setSearchMode(mode: SearchMode) {
        _state.update { it.copy(searchMode = mode, searchResult = SearchOutcome.Idle, inputValid = true) }
    }

    fun setSearchInput(input: String) {
        val valid = when (_state.value.searchMode) {
            SearchMode.HASH -> input.isBlank() || SHA256_REGEX.matches(input.trim())
            SearchMode.PATH -> true // any path is valid
        }
        _state.update { it.copy(searchInput = input, inputValid = valid) }
    }

    fun search(query: String = _state.value.searchInput.trim()) {
        if (query.isBlank()) return
        when (_state.value.searchMode) {
            SearchMode.HASH -> searchByHash(query)
            SearchMode.PATH -> searchByPath(query)
        }
    }

    private fun searchByHash(sha256: String) {
        if (!SHA256_REGEX.matches(sha256)) {
            _state.update { it.copy(inputValid = false) }
            return
        }
        _state.update { it.copy(searchResult = SearchOutcome.Searching) }
        scope.launch {
            // Check quarantine status in parallel
            val quarantineInfo = checkQuarantine(sha256)

            when (val result = cytoolCommands.searchFileHash(sha256)) {
                is CytoolResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        _state.update { it.copy(searchResult = SearchOutcome.Found(
                            results = result.data,
                            isQuarantined = quarantineInfo != null,
                            quarantinePath = quarantineInfo?.second,
                            source = "file_search"
                        )) }
                    } else {
                        // Fallback: check loaded alerts for this hash
                        val alertMatch = findHashInAlerts(sha256)
                        _state.update {
                            if (alertMatch != null) it.copy(searchResult = SearchOutcome.Found(
                                results = listOf(alertMatch),
                                isQuarantined = quarantineInfo != null,
                                quarantinePath = quarantineInfo?.second,
                                source = "alert"
                            ))
                            else if (quarantineInfo != null) it.copy(searchResult = SearchOutcome.Found(
                                results = listOf(FileSearchResult(
                                    sha256 = sha256, md5 = "", fileId = null,
                                    filePath = quarantineInfo.first,
                                    dateCreated = null, dateLastModified = null,
                                    createdByUser = null, createdBySid = null
                                )),
                                isQuarantined = true,
                                quarantinePath = quarantineInfo.second,
                                source = "quarantine"
                            ))
                            else it.copy(searchResult = SearchOutcome.NotFound)
                        }
                    }
                }
                is CytoolResult.Error -> {
                    val alertMatch = findHashInAlerts(sha256)
                    _state.update {
                        if (alertMatch != null) it.copy(searchResult = SearchOutcome.Found(
                            results = listOf(alertMatch),
                            isQuarantined = quarantineInfo != null,
                            quarantinePath = quarantineInfo?.second,
                            source = "alert"
                        ))
                        else it.copy(searchResult = SearchOutcome.Error(result.message))
                    }
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(searchResult = SearchOutcome.Error("Search timed out"))
                }
            }
        }
    }

    /** Check if hash is in quarantine. Returns (originalPath, backupPath) or null. */
    private suspend fun checkQuarantine(sha256: String): Pair<String, String>? {
        return when (val result = cytoolCommands.quarantineList()) {
            is CytoolResult.Success -> {
                result.data.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
                    ?.let { it.filePath to it.backupPath }
            }
            else -> null
        }
    }

    /**
     * Search loaded security alerts for a hash match.
     * Returns a FileSearchResult built from alert data if found.
     */
    private fun findHashInAlerts(sha256: String): FileSearchResult? {
        val alerts = logRepository?.alerts?.value ?: return null
        val match = alerts.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) } ?: return null
        return FileSearchResult(
            sha256 = match.sha256 ?: sha256,
            md5 = match.md5 ?: "",
            fileId = null,
            filePath = match.filePath ?: match.processPath ?: "(from alert: ${match.description})",
            dateCreated = null,
            dateLastModified = match.timestamp.toString(),
            createdByUser = match.user,
            createdBySid = match.userSid
        )
    }

    private fun searchByPath(path: String) {
        _state.update { it.copy(searchResult = SearchOutcome.Searching) }
        scope.launch {
            when (val result = cytoolCommands.searchFilePath(path)) {
                is CytoolResult.Success -> {
                    _state.update {
                        if (result.data.isEmpty()) it.copy(searchResult = SearchOutcome.NotFound)
                        else it.copy(searchResult = SearchOutcome.Found(result.data))
                    }
                }
                is CytoolResult.Error -> _state.update {
                    it.copy(searchResult = SearchOutcome.Error(result.message))
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(searchResult = SearchOutcome.Error("Search timed out"))
                }
            }
        }
    }

    // Keep old name for compatibility with hash browser click
    fun searchHash(sha256: String = _state.value.searchInput.trim()) {
        _state.update { it.copy(searchMode = SearchMode.HASH, searchInput = sha256) }
        searchByHash(sha256)
    }

    fun refreshHashDatabase() {
        scope.launch {
            _state.update { it.copy(scanInProgress = true, scanStatus = "Starting...") }
            when (cytoolCommands.startFileSystemScan()) {
                is CytoolResult.Success -> pollScanProgress()
                is CytoolResult.Error -> _state.update {
                    it.copy(scanInProgress = false, error = "Failed to start scan")
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(scanInProgress = false, error = "Scan start timed out")
                }
            }
        }
    }

    private fun pollScanProgress() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _state.value.scanInProgress) {
                delay(2.seconds)
                when (val result = cytoolCommands.queryFileSystemScan()) {
                    is CytoolResult.Success -> {
                        val status = result.data
                        val done = status.contains("complete", ignoreCase = true) ||
                            status.contains("idle", ignoreCase = true) ||
                            status.contains("not running", ignoreCase = true)
                        _state.update {
                            it.copy(
                                scanStatus = status,
                                scanInProgress = !done
                            )
                        }
                        if (done) break
                    }
                    else -> {
                        _state.update { it.copy(scanInProgress = false, scanStatus = "Query failed") }
                        break
                    }
                }
            }
        }
    }

    fun loadHashEntries() {
        scope.launch {
            _state.update { it.copy(hashEntriesLoading = true) }
            when (val result = cytoolCommands.printDatabase("file_id_hash.db")) {
                is CytoolResult.Success -> {
                    try {
                        val entries = json.decodeFromString<List<FileIdHashEntry>>(result.data)
                        _state.update { it.copy(hashEntries = entries, hashEntriesLoading = false) }
                    } catch (e: Exception) {
                        _state.update { it.copy(hashEntriesLoading = false, error = "Failed to parse hash DB: ${e.message}") }
                    }
                }
                is CytoolResult.Error -> _state.update {
                    it.copy(hashEntriesLoading = false, error = result.message)
                }
                is CytoolResult.Timeout -> _state.update {
                    it.copy(hashEntriesLoading = false, error = "Timed out loading hash DB")
                }
            }
        }
    }

    fun setHashEntriesFilter(filter: String) {
        _state.update { it.copy(hashEntriesFilter = filter) }
    }

    // --- Blocklist Browser ---

    fun loadBlocklist() {
        scope.launch {
            _state.update { it.copy(blocklistLoading = true) }
            when (val result = cytoolCommands.printDatabase("hash_overrides.db")) {
                is CytoolResult.Success -> {
                    try {
                        val arr = json.parseToJsonElement(result.data).jsonArray
                        val entries = arr.mapNotNull { el ->
                            val obj = el.jsonObject
                            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val verdict = obj["value"]?.jsonObject?.get("verdict")?.jsonPrimitive?.intOrNull ?: 0
                            BlocklistEntry(key, verdict)
                        }
                        _state.update { it.copy(blocklistEntries = entries, blocklistLoading = false) }
                    } catch (e: Exception) {
                        _state.update { it.copy(blocklistLoading = false, error = "Failed to parse blocklist: ${e.message}") }
                    }
                }
                is CytoolResult.Error -> _state.update { it.copy(blocklistLoading = false, error = result.message) }
                is CytoolResult.Timeout -> _state.update { it.copy(blocklistLoading = false, error = "Timed out loading blocklist") }
            }
        }
    }

    // --- IoC Management ---

    fun importIocList(path: Path) {
        iocManager.parseFile(path)
            .onSuccess { entries ->
                val current = _state.value.iocList.toMutableList()
                val existing = current.map { it.sha256 }.toSet()
                val newEntries = entries.filter { it.sha256 !in existing }
                current.addAll(newEntries)
                _state.update {
                    it.copy(
                        iocList = current,
                        message = "Imported ${newEntries.size} IoCs (${entries.size - newEntries.size} duplicates skipped)"
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(error = "Import failed: ${e.message}") }
            }
    }

    fun addIocManually(sha256: String, description: String) {
        if (!iocManager.validateHash(sha256)) {
            _state.update { it.copy(error = "Invalid SHA256 format") }
            return
        }
        val entry = IocEntry(sha256.lowercase().trim(), description, "manual")
        val current = _state.value.iocList.toMutableList()
        if (current.any { it.sha256 == entry.sha256 }) {
            _state.update { it.copy(error = "IoC already in list") }
            return
        }
        current.add(entry)
        _state.update { it.copy(iocList = current, error = null) }
    }

    fun removeIoc(sha256: String) {
        _state.update { it.copy(iocList = it.iocList.filter { ioc -> ioc.sha256 != sha256 }) }
    }

    fun clearIocList() {
        _state.update { it.copy(iocList = emptyList(), iocSearchResults = emptyList()) }
    }

    fun runBatchSearch() {
        val iocs = _state.value.iocList
        if (iocs.isEmpty()) return

        _state.update { it.copy(iocBatchSearching = true, iocBatchProgress = 0 to iocs.size, iocSearchResults = emptyList()) }

        scope.launch {
            val results = mutableListOf<IocSearchResult>()
            iocs.forEachIndexed { index, ioc ->
                _state.update { it.copy(iocBatchProgress = (index + 1) to iocs.size) }

                val searchResult = when (val r = cytoolCommands.searchFileHash(ioc.sha256)) {
                    is CytoolResult.Success -> IocSearchResult(ioc, r.data.isNotEmpty(), r.data)
                    else -> IocSearchResult(ioc, false)
                }
                results.add(searchResult)
                _state.update { it.copy(iocSearchResults = results.toList()) }
            }

            val found = results.count { it.found }
            _state.update {
                it.copy(
                    iocBatchSearching = false,
                    iocBatchProgress = null,
                    message = "Found $found of ${iocs.size} IoCs on this endpoint"
                )
            }
        }
    }

    fun exportIocResults(outputPath: Path, format: ExportFormat) {
        iocManager.exportResults(_state.value.iocSearchResults, outputPath, format)
            .onSuccess { _state.update { it.copy(message = "Exported to ${outputPath.fileName}") } }
            .onFailure { e -> _state.update { it.copy(error = "Export failed: ${e.message}") } }
    }

    /** Blacklist a single hash. */
    fun blacklistHash(sha256: String) {
        scope.launch {
            _state.update { it.copy(message = "Adding to blocklist...") }
            when (val result = cytoolCommands.blacklistHashes(listOf(sha256))) {
                is CytoolResult.Success -> _state.update { it.copy(message = result.data) }
                is CytoolResult.Error -> _state.update { it.copy(error = "Blacklist failed: ${result.message}") }
                is CytoolResult.Timeout -> _state.update { it.copy(error = "Blacklist timed out") }
            }
        }
    }

    /**
     * Blacklist all IoC hashes by importing them into hash_overrides.db.
     * This makes the agent treat these hashes as malicious (verdict=2).
     */
    fun blacklistIocs() {
        val hashes = _state.value.iocList.map { it.sha256 }
        if (hashes.isEmpty()) return

        scope.launch {
            _state.update { it.copy(message = "Importing ${hashes.size} hashes into blocklist...") }
            when (val result = cytoolCommands.blacklistHashes(hashes)) {
                is CytoolResult.Success -> _state.update { it.copy(message = result.data) }
                is CytoolResult.Error -> _state.update { it.copy(error = "Blacklist failed: ${result.message}") }
                is CytoolResult.Timeout -> _state.update { it.copy(error = "Blacklist import timed out") }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
