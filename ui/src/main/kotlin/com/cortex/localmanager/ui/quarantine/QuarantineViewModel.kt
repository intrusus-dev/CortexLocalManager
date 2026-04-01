package com.cortex.localmanager.ui.quarantine

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.models.QuarantineEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuarantineState(
    val isLoading: Boolean = false,
    val entries: List<QuarantineEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val confirmAction: ConfirmAction? = null,
    val selectedEntry: QuarantineEntry? = null
)

data class ConfirmAction(
    val type: ActionType,
    val entry: QuarantineEntry,
    val customPath: String? = null
)

enum class ActionType { RESTORE, DELETE }

class QuarantineViewModel(
    private val cytoolCommands: CytoolCommands,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(QuarantineState())
    val state: StateFlow<QuarantineState> = _state.asStateFlow()

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = cytoolCommands.quarantineList()) {
                is CytoolResult.Success -> {
                    _state.update { it.copy(entries = result.data, isLoading = false) }
                }
                is CytoolResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is CytoolResult.Timeout -> {
                    _state.update { it.copy(isLoading = false, error = "Timed out loading quarantine list") }
                }
            }
        }
    }

    fun requestRestore(entry: QuarantineEntry) {
        _state.update { it.copy(confirmAction = ConfirmAction(ActionType.RESTORE, entry)) }
    }

    fun requestDelete(entry: QuarantineEntry) {
        _state.update { it.copy(confirmAction = ConfirmAction(ActionType.DELETE, entry)) }
    }

    fun cancelAction() {
        _state.update { it.copy(confirmAction = null) }
    }

    fun confirmRestore(customPath: String? = null) {
        val action = _state.value.confirmAction ?: return
        _state.update { it.copy(confirmAction = null, isLoading = true) }

        scope.launch {
            when (val result = cytoolCommands.quarantineRestore(action.entry.quarantineId, customPath)) {
                is CytoolResult.Success -> {
                    _state.update { it.copy(
                        isLoading = false,
                        message = "Restored: ${action.entry.filePath.substringAfterLast("\\")}"
                    )}
                    refresh()
                }
                is CytoolResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = "Restore failed: ${result.message}") }
                }
                is CytoolResult.Timeout -> {
                    _state.update { it.copy(isLoading = false, error = "Restore timed out") }
                }
            }
        }
    }

    fun confirmDelete() {
        val action = _state.value.confirmAction ?: return
        _state.update { it.copy(confirmAction = null, isLoading = true) }

        scope.launch {
            when (val result = cytoolCommands.quarantineDelete(action.entry.quarantineId)) {
                is CytoolResult.Success -> {
                    _state.update { it.copy(
                        isLoading = false,
                        message = "Deleted: ${action.entry.filePath.substringAfterLast("\\")}"
                    )}
                    refresh()
                }
                is CytoolResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = "Delete failed: ${result.message}") }
                }
                is CytoolResult.Timeout -> {
                    _state.update { it.copy(isLoading = false, error = "Delete timed out") }
                }
            }
        }
    }

    fun selectEntry(entry: QuarantineEntry?) {
        _state.update { it.copy(selectedEntry = entry) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
