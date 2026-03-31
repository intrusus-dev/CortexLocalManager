package com.cortex.localmanager.core.cytool

sealed class CytoolResult<out T> {
    data class Success<T>(val data: T, val rawOutput: String) : CytoolResult<T>()
    data class Error(val message: String, val rawOutput: String, val exitCode: Int) : CytoolResult<Nothing>()
    data class Timeout(val command: String, val timeoutSeconds: Long) : CytoolResult<Nothing>()
}
