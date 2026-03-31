package com.cortex.localmanager.ui

import androidx.compose.runtime.MutableState
import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolExecutor
import com.cortex.localmanager.core.logs.EdrLogParser
import com.cortex.localmanager.core.logs.LogRepository
import com.cortex.localmanager.core.logs.LogWatcher
import com.cortex.localmanager.core.logs.PreventionLogParser
import com.cortex.localmanager.core.suex.SuexManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

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
}
