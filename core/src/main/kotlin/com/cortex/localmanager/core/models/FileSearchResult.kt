package com.cortex.localmanager.core.models

data class FileSearchResult(
    val sha256: String,
    val md5: String,
    val fileId: String?,
    val filePath: String,
    val dateCreated: String?,
    val dateLastModified: String?,
    val createdByUser: String?,
    val createdBySid: String?,
    val found: Boolean = true
)
