package com.cortex.localmanager.ui.forensics

import com.cortex.localmanager.core.forensics.CollectionProgress
import com.cortex.localmanager.core.forensics.ForensicCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

data class ForensicsState(
    val progress: CollectionProgress = CollectionProgress(),
    val error: String? = null
)

class ForensicsViewModel(
    private val collector: ForensicCollector,
    private val passwordProvider: () -> String?,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ForensicsState())
    val state: StateFlow<ForensicsState> = _state.asStateFlow()

    init {
        scope.launch {
            collector.progress.collect { progress ->
                _state.update { it.copy(progress = progress) }
            }
        }
    }

    fun startCollection(outputDir: Path) {
        val password = passwordProvider()
        if (password == null) {
            _state.update { it.copy(error = "Supervisor password required for forensic collection") }
            return
        }

        scope.launch {
            _state.update { it.copy(error = null) }
            try {
                collector.collectAll(outputDir, password)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Collection failed: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
