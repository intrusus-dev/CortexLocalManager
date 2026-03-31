package com.cortex.localmanager.ui.hunting

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.hunting.ExportFormat
import com.cortex.localmanager.core.hunting.IocEntry
import com.cortex.localmanager.core.hunting.IocListManager
import com.cortex.localmanager.core.hunting.IocSearchResult
import com.cortex.localmanager.core.models.FileIdHashEntry
import com.cortex.localmanager.core.models.FileSearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

sealed class SearchOutcome {
    data object Idle : SearchOutcome()
    data object Searching : SearchOutcome()
    data class Found(val results: List<FileSearchResult>) : SearchOutcome()
    data object NotFound : SearchOutcome()
    data class Error(val message: String) : SearchOutcome()
}

data class HuntingState(
    val searchInput: String = "",
    val inputValid: Boolean = true,
    val searchResult: SearchOutcome = SearchOutcome.Idle,
    val scanInProgress: Boolean = false,
    val scanStatus: String? = null,
    val hashEntries: List<FileIdHashEntry> = emptyList(),
    val hashEntriesLoading: Boolean = false,
    val hashEntriesFilter: String = "",
    // IoC
    val iocList: List<IocEntry> = emptyList(),
    val iocSearchResults: List<IocSearchResult> = emptyList(),
    val iocBatchSearching: Boolean = false,
    val iocBatchProgress: Pair<Int, Int>? = null,
    val message: String? = null,
    val error: String? = null
)

private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class HuntingViewModel(
    private val cytoolCommands: CytoolCommands,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(HuntingState())
    val state: StateFlow<HuntingState> = _state.asStateFlow()
    private val iocManager = IocListManager()

    private var pollJob: Job? = null

    fun setSearchInput(input: String) {
        val valid = input.isBlank() || SHA256_REGEX.matches(input.trim())
        _state.update { it.copy(searchInput = input, inputValid = valid) }
    }

    fun searchHash(sha256: String = _state.value.searchInput.trim()) {
        if (!SHA256_REGEX.matches(sha256)) {
            _state.update { it.copy(inputValid = false) }
            return
        }
        _state.update { it.copy(searchResult = SearchOutcome.Searching) }
        scope.launch {
            when (val result = cytoolCommands.searchFileHash(sha256)) {
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

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
