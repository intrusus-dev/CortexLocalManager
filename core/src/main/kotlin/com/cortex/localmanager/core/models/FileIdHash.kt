package com.cortex.localmanager.core.models

import kotlinx.serialization.Serializable

@Serializable
data class FileIdHashEntry(
    val key: String,
    val value: FileIdHashValue
)

@Serializable
data class FileIdHashValue(
    val lruData: LruData,
    val sha256: String
)

@Serializable
data class LruData(
    val lastUsed: String,
    val index: String
)
