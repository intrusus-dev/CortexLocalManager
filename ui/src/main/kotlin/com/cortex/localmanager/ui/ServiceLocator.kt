package com.cortex.localmanager.ui

import androidx.compose.runtime.MutableState
import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolExecutor
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.logs.EdrLogParser
import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.logs.LogWatcher
import com.cortex.localmanager.core.logs.PreventionLogParser
import com.cortex.localmanager.core.suex.SuexManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ServiceLocator(val supervisorPassword: MutableState<String?>) {
    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    val cytoolExecutor by lazy { CytoolExecutor() }
    val cytoolCommands by lazy {
        CytoolCommands(cytoolExecutor, passwordProvider = { supervisorPassword.value })
    }
    val edrLogParser by lazy { EdrLogParser() }
    val preventionLogParser by lazy { PreventionLogParser() }
    val logWatcher by lazy {
        LogWatcher(
            edrLogParser = edrLogParser,
            preventionLogParser = preventionLogParser,
            scope = backgroundScope
        )
    }
    val logRepository by lazy {
        LogRepository(edrLogParser, preventionLogParser, logWatcher)
    }
    val suexManager by lazy { SuexManager() }

    fun initialize() {
        backgroundScope.launch {
            // Load existing logs from disk and Windows Event Log
            logRepository.loadExisting()
            // Start watching for new log files
            logWatcher.start()
            logRepository.startWatching(backgroundScope)
        }
    }

    /**
     * Load rich security events from cytool. Call when password becomes available.
     */
    fun loadSecurityEvents() {
        backgroundScope.launch {
            println("Fetching security events from cytool...")
            when (val result = cytoolCommands.getSecurityEvents()) {
                is CytoolResult.Success -> {
                    logRepository.loadSecurityEvents(result.data)
                }
                is CytoolResult.Error -> {
                    println("Failed to load security events: ${result.message}")
                }
                is CytoolResult.Timeout -> {
                    println("Security events fetch timed out")
                }
            }
        }
    }
}
