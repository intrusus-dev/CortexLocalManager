package com.cortex.localmanager.ui.agentcontrol

import com.cortex.localmanager.core.cytool.CytoolCommands
import com.cortex.localmanager.core.cytool.CytoolResult
import com.cortex.localmanager.core.models.ProtectionFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuntimeComponent(val name: String, val state: String, val description: String)

data class AgentControlState(
    val isLoading: Boolean = false,
    val protectionFeatures: List<ProtectionFeature> = emptyList(),
    val runtimeComponents: List<RuntimeComponent> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

private val COMPONENT_DESCRIPTIONS = mapOf(
    "cyverak" to "Kernel driver — core agent protection engine",
    "cyvrmtgn" to "Remote management — cloud communication & live terminal",
    "cyvrfsfd" to "File system filter driver — file monitoring & quarantine",
    "cyserver" to "Core service — agent orchestration & policy engine",
    "tedrdrv" to "EDR driver — telemetry collection & behavioral analysis",
    "telam" to "Early Launch Anti-Malware — boot-time protection"
)

class AgentControlViewModel(
    private val cytoolCommands: CytoolCommands,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AgentControlState())
    val state: StateFlow<AgentControlState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val protectDeferred = async { cytoolCommands.queryProtection() }
            val runtimeDeferred = async { cytoolCommands.runtimeQuery() }

            when (val r = protectDeferred.await()) {
                is CytoolResult.Success -> _state.update { it.copy(protectionFeatures = r.data) }
                is CytoolResult.Error -> _state.update { it.copy(error = "Protection: ${r.message}") }
                else -> {}
            }

            when (val r = runtimeDeferred.await()) {
                is CytoolResult.Success -> _state.update { it.copy(
                    runtimeComponents = r.data.map { c ->
                        RuntimeComponent(c.name, c.state, COMPONENT_DESCRIPTIONS[c.name] ?: "Agent component")
                    }
                )}
                is CytoolResult.Error -> _state.update { it.copy(error = "Runtime: ${r.message}") }
                else -> {}
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    fun disableProtection(feature: String) {
        scope.launch {
            when (val r = cytoolCommands.protectAction("disable", feature)) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "Disabled $feature protection") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = r.message) }
                else -> {}
            }
        }
    }

    fun enableProtection(feature: String) {
        scope.launch {
            when (val r = cytoolCommands.protectAction("enable", feature)) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "Enabled $feature protection") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = r.message) }
                else -> {}
            }
        }
    }

    fun resetProtectionToPolicy() {
        scope.launch {
            when (val r = cytoolCommands.protectAction("policy")) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "Reset all protection to policy") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = r.message) }
                else -> {}
            }
        }
    }

    fun stopComponent(name: String) {
        scope.launch {
            _state.update { it.copy(message = "Stopping $name...") }
            when (val r = cytoolCommands.runtimeStop(name)) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "Stopped $name") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = "Stop $name: ${r.message}") }
                else -> {}
            }
        }
    }

    fun startComponent(name: String) {
        scope.launch {
            _state.update { it.copy(message = "Starting $name...") }
            when (val r = cytoolCommands.runtimeStart(name)) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "Started $name") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = "Start $name: ${r.message}") }
                else -> {}
            }
        }
    }

    fun stopAllComponents() {
        scope.launch {
            _state.update { it.copy(message = "Stopping all components...") }
            when (val r = cytoolCommands.runtimeStop()) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "All components stopped") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = r.message) }
                else -> {}
            }
        }
    }

    fun startAllComponents() {
        scope.launch {
            _state.update { it.copy(message = "Starting all components...") }
            when (val r = cytoolCommands.runtimeStart()) {
                is CytoolResult.Success -> { _state.update { it.copy(message = "All components started") }; refresh() }
                is CytoolResult.Error -> _state.update { it.copy(error = r.message) }
                else -> {}
            }
        }
    }

    fun dismissMessage() { _state.update { it.copy(message = null, error = null) } }
}
