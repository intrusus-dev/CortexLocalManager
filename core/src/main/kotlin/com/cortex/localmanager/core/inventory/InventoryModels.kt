package com.cortex.localmanager.core.inventory

import kotlinx.datetime.Instant

data class EndpointInventory(
    val collectedAt: Instant,
    val system: SystemInfo,
    val processes: List<RunningProcess>,
    val sessions: List<UserSession>,
    val network: List<NetworkInterface>,
    val networkConnections: List<NetworkConnection>
)

data class SystemInfo(
    val hostname: String,
    val domain: String?,
    val osName: String?,
    val osVersion: String?,
    val osBuild: String?,
    val architecture: String?,
    val manufacturer: String?,
    val model: String?,
    val totalMemoryMb: Long?,
    val availableMemoryMb: Long?,
    val cpuName: String?,
    val cpuCores: Int?,
    val uptime: String?,
    val lastBoot: String?,
    val currentUser: String?,
    val systemDrive: String?
)

data class RunningProcess(
    val pid: Long,
    val name: String,
    val sessionId: Int?,
    val memoryKb: Long?,
    val cpuTime: String?,
    val user: String?,
    val commandLine: String?,
    val parentPid: Long?,
    val path: String?
)

data class UserSession(
    val username: String,
    val sessionName: String?,
    val sessionId: Int,
    val state: String,
    val idleTime: String?,
    val logonTime: String?
)

data class NetworkInterface(
    val name: String,
    val description: String?,
    val macAddress: String?,
    val ipAddresses: List<String>,
    val subnet: String?,
    val gateway: String?,
    val dnsServers: List<String>,
    val status: String?
)

data class NetworkConnection(
    val protocol: String,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String?,
    val remotePort: Int?,
    val state: String?,
    val pid: Long?,
    val processName: String?
)
