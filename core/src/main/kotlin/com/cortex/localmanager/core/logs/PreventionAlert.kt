package com.cortex.localmanager.core.logs

import kotlinx.datetime.Instant

data class PreventionAlert(
    val timestamp: Instant,
    val processPath: String?,
    val pid: Long?,
    val blocked: Boolean,
    val trigger: String?,
    val componentName: String?,
    val operationStatus: String?,
    val description: String?,
    val sha256: String?,
    val md5: String?,
    val filePath: String?,
    val user: String?,
    val userSid: String?,
    val osVersion: String?,
    val sourceFile: String?
)
