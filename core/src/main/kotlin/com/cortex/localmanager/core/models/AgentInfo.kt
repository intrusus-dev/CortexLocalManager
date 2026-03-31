package com.cortex.localmanager.core.models

data class AgentInfo(
    val contentType: Int,
    val contentBuild: Int,
    val contentVersion: String,
    val eventLog: Int
)
