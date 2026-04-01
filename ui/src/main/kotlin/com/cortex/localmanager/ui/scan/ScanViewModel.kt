package com.cortex.localmanager.ui.scan

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.seconds

data class ScanState(
    // On-demand scan
    val onDemandStatus: String? = null,
    val onDemandRunning: Boolean = false,
    val lastScanTime: String? = null,
    // File system scan (hash DB refresh)
    val fileSystemScanStatus: String? = null,
    val fileSystemScanRunning: Boolean = false,
    // Unified scans
    val dlpScanStatus: String? = null,
    val dlpScanRunning: Boolean = false,
    val amfScanStatus: String? = null,
    val amfScanRunning: Boolean = false,
    // General
    val error: String? = null,
    val message: String? = null
)

class ScanViewModel(
    private val cytoolCommands: CytoolCommands,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var onDemandPollJob: Job? = null
    private var fsScanPollJob: Job? = null
    private var dlpPollJob: Job? = null
    private var amfPollJob: Job? = null

    fun refresh() {
        queryOnDemandStatus()
        queryLastScanTime()
        queryFileSystemScanStatus()
        queryUnifiedStatus("dlp")
        queryUnifiedStatus("amf")
    }

    // --- On-Demand Scan ---

    fun startOnDemandScan() {
        scope.launch {
            when (val r = cytoolCommands.scanStart()) {
                is CytoolResult.Success -> {
                    _state.update { it.copy(onDemandRunning = true, onDemandStatus = "Starting...", message = "On-demand scan started") }
                    pollOnDemandStatus()
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Start scan failed: ${r.message}") }
                is CytoolResult.Timeout -> _state.update { it.copy(error = "Start scan timed out") }
            }
        }
    }

    fun stopOnDemandScan() {
        scope.launch {
            when (val r = cytoolCommands.scanStop()) {
                is CytoolResult.Success -> {
                    onDemandPollJob?.cancel()
                    _state.update { it.copy(onDemandRunning = false, onDemandStatus = "Stopped", message = "Scan stopped") }
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Stop scan failed: ${r.message}") }
                is CytoolResult.Timeout -> _state.update { it.copy(error = "Stop scan timed out") }
            }
        }
    }

    private fun queryOnDemandStatus() {
        scope.launch {
            when (val r = cytoolCommands.scanQuery()) {
                is CytoolResult.Success -> {
                    val running = r.data.contains("running", ignoreCase = true) ||
                        r.data.contains("progress", ignoreCase = true)
                    _state.update { it.copy(onDemandStatus = r.data, onDemandRunning = running) }
                    if (running) pollOnDemandStatus()
                }
                is CytoolResult.Error -> _state.update { it.copy(onDemandStatus = "Query failed") }
                else -> {}
            }
        }
    }

    private fun queryLastScanTime() {
        scope.launch {
            when (val r = cytoolCommands.scanLastScanTime()) {
                is CytoolResult.Success -> _state.update { it.copy(lastScanTime = r.data) }
                else -> {}
            }
        }
    }

    private fun pollOnDemandStatus() {
        onDemandPollJob?.cancel()
        onDemandPollJob = scope.launch {
            while (isActive && _state.value.onDemandRunning) {
                delay(3.seconds)
                when (val r = cytoolCommands.scanQuery()) {
                    is CytoolResult.Success -> {
                        val done = r.data.contains("complete", ignoreCase = true) ||
                            r.data.contains("idle", ignoreCase = true) ||
                            r.data.contains("not running", ignoreCase = true) ||
                            r.data.contains("done", ignoreCase = true) ||
                            r.data.contains("finished", ignoreCase = true)
                        _state.update { it.copy(onDemandStatus = r.data, onDemandRunning = !done) }
                        if (done) {
                            queryLastScanTime()
                            break
                        }
                    }
                    else -> break
                }
            }
        }
    }

    // --- File System Scan ---

    fun startFileSystemScan() {
        scope.launch {
            when (val r = cytoolCommands.startFileSystemScan()) {
                is CytoolResult.Success -> {
                    _state.update { it.copy(fileSystemScanRunning = true, fileSystemScanStatus = "Starting...", message = "Hash DB refresh started") }
                    pollFileSystemScan()
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Start FS scan failed: ${r.message}") }
                is CytoolResult.Timeout -> _state.update { it.copy(error = "Start FS scan timed out") }
            }
        }
    }

    fun stopFileSystemScan() {
        scope.launch {
            when (val r = cytoolCommands.stopFileSystemScan()) {
                is CytoolResult.Success -> {
                    fsScanPollJob?.cancel()
                    _state.update { it.copy(fileSystemScanRunning = false, fileSystemScanStatus = "Stopped") }
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Stop FS scan failed: ${r.message}") }
                else -> {}
            }
        }
    }

    private fun queryFileSystemScanStatus() {
        scope.launch {
            when (val r = cytoolCommands.queryFileSystemScan()) {
                is CytoolResult.Success -> {
                    val running = !r.data.contains("complete", ignoreCase = true) &&
                        !r.data.contains("idle", ignoreCase = true) &&
                        !r.data.contains("not running", ignoreCase = true)
                    _state.update { it.copy(fileSystemScanStatus = r.data, fileSystemScanRunning = running) }
                    if (running) pollFileSystemScan()
                }
                else -> {}
            }
        }
    }

    private fun pollFileSystemScan() {
        fsScanPollJob?.cancel()
        fsScanPollJob = scope.launch {
            while (isActive && _state.value.fileSystemScanRunning) {
                delay(2.seconds)
                when (val r = cytoolCommands.queryFileSystemScan()) {
                    is CytoolResult.Success -> {
                        val done = r.data.contains("complete", ignoreCase = true) ||
                            r.data.contains("idle", ignoreCase = true) ||
                            r.data.contains("not running", ignoreCase = true) ||
                            r.data.contains("done", ignoreCase = true) ||
                            r.data.contains("finished", ignoreCase = true)
                        _state.update { it.copy(fileSystemScanStatus = r.data, fileSystemScanRunning = !done) }
                        if (done) break
                    }
                    else -> break
                }
            }
        }
    }

    // --- Unified Scans (DLP/AMF) ---

    fun startUnifiedScan(type: String) {
        scope.launch {
            when (val r = cytoolCommands.unifiedScanStart(type)) {
                is CytoolResult.Success -> {
                    updateUnified(type, status = "Starting...", running = true)
                    _state.update { it.copy(message = "${type.uppercase()} scan started") }
                    pollUnifiedScan(type)
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Start $type scan: ${r.message}") }
                else -> {}
            }
        }
    }

    fun stopUnifiedScan(type: String) {
        scope.launch {
            when (cytoolCommands.unifiedScanStop(type)) {
                is CytoolResult.Success -> {
                    cancelUnifiedPoll(type)
                    updateUnified(type, status = "Stopped", running = false)
                }
                is CytoolResult.Error -> _state.update { it.copy(error = "Stop $type scan failed") }
                else -> {}
            }
        }
    }

    fun pauseUnifiedScan(type: String) {
        scope.launch {
            when (cytoolCommands.unifiedScanPause(type)) {
                is CytoolResult.Success -> {
                    cancelUnifiedPoll(type)
                    updateUnified(type, status = "Paused", running = false)
                }
                else -> {}
            }
        }
    }

    fun resumeUnifiedScan(type: String) {
        scope.launch {
            when (cytoolCommands.unifiedScanResume(type)) {
                is CytoolResult.Success -> {
                    updateUnified(type, status = "Resuming...", running = true)
                    pollUnifiedScan(type)
                }
                else -> {}
            }
        }
    }

    private fun queryUnifiedStatus(type: String) {
        scope.launch {
            when (val r = cytoolCommands.unifiedScanQuery(type)) {
                is CytoolResult.Success -> {
                    val running = r.data.contains("running", ignoreCase = true) ||
                        r.data.contains("progress", ignoreCase = true)
                    updateUnified(type, status = r.data, running = running)
                    if (running) pollUnifiedScan(type)
                }
                else -> {}
            }
        }
    }

    private fun pollUnifiedScan(type: String) {
        cancelUnifiedPoll(type)
        val job = scope.launch {
            while (isActive) {
                delay(3.seconds)
                when (val r = cytoolCommands.unifiedScanQuery(type)) {
                    is CytoolResult.Success -> {
                        val done = r.data.contains("complete", ignoreCase = true) ||
                            r.data.contains("idle", ignoreCase = true) ||
                            r.data.contains("not running", ignoreCase = true) ||
                            r.data.contains("done", ignoreCase = true) ||
                            r.data.contains("finished", ignoreCase = true)
                        updateUnified(type, status = r.data, running = !done)
                        if (done) break
                    }
                    else -> break
                }
            }
        }
        when (type) { "dlp" -> dlpPollJob = job; "amf" -> amfPollJob = job }
    }

    private fun cancelUnifiedPoll(type: String) {
        when (type) { "dlp" -> dlpPollJob?.cancel(); "amf" -> amfPollJob?.cancel() }
    }

    private fun updateUnified(type: String, status: String, running: Boolean) {
        _state.update {
            when (type) {
                "dlp" -> it.copy(dlpScanStatus = status, dlpScanRunning = running)
                "amf" -> it.copy(amfScanStatus = status, amfScanRunning = running)
                else -> it
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
