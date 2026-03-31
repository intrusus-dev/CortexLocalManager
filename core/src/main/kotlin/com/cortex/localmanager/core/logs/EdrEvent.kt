package com.cortex.localmanager.core.logs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EdrEvent(
    val timestamp: String? = null,
    val eventType: String? = null,
    val processPath: String? = null,
    val commandLine: String? = null,
    val pid: Long? = null,
    val parentPid: Long? = null,
    val user: String? = null,
    val sha256: String? = null,
    val filePath: String? = null,
    val registryKey: String? = null,
    val networkDestination: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val sourceFile: String? = null,
    val rawData: JsonObject? = null
)
