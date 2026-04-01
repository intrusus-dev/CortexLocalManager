package com.cortex.localmanager.core.models

import kotlinx.datetime.Instant

data class QuarantineEntry(
    val quarantineId: String,
    val filePath: String,
    val backupPath: String,
    val sha256: String,
    val size: Long,
    val actionId: String,
    val timestamp: Instant,
    val status: String,
    val applicationName: String
)
