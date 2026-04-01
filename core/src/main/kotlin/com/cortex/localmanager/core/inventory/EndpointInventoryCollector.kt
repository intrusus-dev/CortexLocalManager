package com.cortex.localmanager.core.inventory

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Collects endpoint inventory using PowerShell (Get-CimInstance) and standard Windows commands.
 * Works on both x64 and ARM64 Windows 10/11 — does NOT depend on deprecated wmic.
 */
class EndpointInventoryCollector {

    suspend fun collect(): EndpointInventory = coroutineScope {
        logger.info { "Collecting endpoint inventory..." }

        val systemDeferred = async { collectSystemInfo() }
        val processesDeferred = async { collectProcesses() }
        val sessionsDeferred = async { collectSessions() }
        val networkDeferred = async { collectNetworkInterfaces() }
        val connectionsDeferred = async { collectNetworkConnections() }

        val inventory = EndpointInventory(
            collectedAt = Clock.System.now(),
            system = systemDeferred.await(),
            processes = processesDeferred.await(),
            sessions = sessionsDeferred.await(),
            network = networkDeferred.await(),
            networkConnections = connectionsDeferred.await()
        )

        logger.info {
            "Inventory collected: ${inventory.processes.size} processes, " +
                "${inventory.sessions.size} sessions, ${inventory.network.size} interfaces, " +
                "${inventory.networkConnections.size} connections"
        }
        inventory
    }

    // --- System Info via PowerShell Get-CimInstance ---

    private suspend fun collectSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val hostname = runCmd("hostname")?.trim() ?: "unknown"

        // Single PowerShell call for all system info
        val psScript = """
            ${'$'}os = Get-CimInstance Win32_OperatingSystem
            ${'$'}cs = Get-CimInstance Win32_ComputerSystem
            ${'$'}cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
            Write-Output "Caption=${'$'}(${'$'}os.Caption)"
            Write-Output "Version=${'$'}(${'$'}os.Version)"
            Write-Output "BuildNumber=${'$'}(${'$'}os.BuildNumber)"
            Write-Output "OSArchitecture=${'$'}(${'$'}os.OSArchitecture)"
            Write-Output "TotalVisibleMemorySize=${'$'}(${'$'}os.TotalVisibleMemorySize)"
            Write-Output "FreePhysicalMemory=${'$'}(${'$'}os.FreePhysicalMemory)"
            Write-Output "LastBootUpTime=${'$'}(${'$'}os.LastBootUpTime.ToString('yyyy-MM-dd HH:mm:ss'))"
            Write-Output "Domain=${'$'}(${'$'}cs.Domain)"
            Write-Output "Manufacturer=${'$'}(${'$'}cs.Manufacturer)"
            Write-Output "Model=${'$'}(${'$'}cs.Model)"
            Write-Output "UserName=${'$'}(${'$'}cs.UserName)"
            Write-Output "CpuName=${'$'}(${'$'}cpu.Name)"
            Write-Output "NumberOfCores=${'$'}(${'$'}cpu.NumberOfCores)"
        """.trimIndent()

        val output = runPowerShell(psScript)
        val props = parseKeyValueOutput(output)

        val totalMemKb = props["TotalVisibleMemorySize"]?.toLongOrNull()
        val freeMemKb = props["FreePhysicalMemory"]?.toLongOrNull()

        SystemInfo(
            hostname = hostname,
            domain = props["Domain"],
            osName = props["Caption"],
            osVersion = props["Version"],
            osBuild = props["BuildNumber"],
            architecture = props["OSArchitecture"],
            manufacturer = props["Manufacturer"],
            model = props["Model"],
            totalMemoryMb = totalMemKb?.let { it / 1024 },
            availableMemoryMb = freeMemKb?.let { it / 1024 },
            cpuName = props["CpuName"],
            cpuCores = props["NumberOfCores"]?.toIntOrNull(),
            uptime = null,
            lastBoot = props["LastBootUpTime"],
            currentUser = props["UserName"],
            systemDrive = System.getenv("SystemDrive")
        )
    }

    // --- Processes via tasklist + PowerShell for paths ---

    private suspend fun collectProcesses(): List<RunningProcess> = withContext(Dispatchers.IO) {
        // tasklist /FO CSV is reliable on all Windows versions
        val output = runCmd("tasklist", "/FO", "CSV", "/V") ?: return@withContext emptyList()

        val lines = output.lines().filter { it.startsWith("\"") }
        if (lines.size < 2) return@withContext emptyList()

        // Parse CSV header
        val header = parseCsvLine(lines.first())
        val nameIdx = header.indexOfFirst { it.equals("Image Name", ignoreCase = true) }
        val pidIdx = header.indexOfFirst { it.equals("PID", ignoreCase = true) }
        val sessIdx = header.indexOfFirst { it.equals("Session#", ignoreCase = true) }
        val memIdx = header.indexOfFirst { it.equals("Mem Usage", ignoreCase = true) }
        val userIdx = header.indexOfFirst { it.equals("User Name", ignoreCase = true) }
        val cpuIdx = header.indexOfFirst { it.equals("CPU Time", ignoreCase = true) }

        val processes = lines.drop(1).mapNotNull { line ->
            val cols = parseCsvLine(line)
            val pid = cols.getOrNull(pidIdx)?.toLongOrNull() ?: return@mapNotNull null
            val name = cols.getOrNull(nameIdx) ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null

            val memStr = cols.getOrNull(memIdx)?.replace(",", "")?.replace(".", "")
                ?.replace(" K", "")?.replace(" KB", "")?.trim()

            RunningProcess(
                pid = pid,
                name = name,
                sessionId = cols.getOrNull(sessIdx)?.toIntOrNull(),
                memoryKb = memStr?.toLongOrNull(),
                cpuTime = cols.getOrNull(cpuIdx),
                user = cols.getOrNull(userIdx)?.takeIf { it != "N/A" },
                commandLine = null,
                parentPid = null,
                path = null
            )
        }.sortedByDescending { it.memoryKb ?: 0 }

        logger.info { "Collected ${processes.size} processes via tasklist" }
        processes
    }

    /** Parse a CSV line handling quoted fields */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            if (line[i] == '"') {
                val end = line.indexOf('"', i + 1)
                if (end >= 0) {
                    fields.add(line.substring(i + 1, end))
                    i = end + 2 // skip closing quote + comma
                } else {
                    fields.add(line.substring(i + 1))
                    break
                }
            } else {
                val end = line.indexOf(',', i)
                if (end >= 0) {
                    fields.add(line.substring(i, end))
                    i = end + 1
                } else {
                    fields.add(line.substring(i))
                    break
                }
            }
        }
        return fields
    }

    // --- Sessions via query user ---

    private suspend fun collectSessions(): List<UserSession> = withContext(Dispatchers.IO) {
        val output = runCmd("query", "user") ?: return@withContext emptyList()

        output.lines().drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val username = line.substring(1, minOf(23, line.length)).trim()
            if (username.isBlank()) return@mapNotNull null

            val rest = if (line.length > 23) line.substring(23) else ""
            val parts = rest.trim().split("\\s{2,}".toRegex())

            val hasSessionName = parts.size >= 4
            val offset = if (hasSessionName) 0 else -1

            UserSession(
                username = username,
                sessionName = if (hasSessionName) parts.getOrNull(0)?.trim() else null,
                sessionId = parts.getOrNull(1 + offset)?.trim()?.toIntOrNull() ?: 0,
                state = parts.getOrNull(2 + offset)?.trim() ?: "Unknown",
                idleTime = parts.getOrNull(3 + offset)?.trim(),
                logonTime = parts.getOrNull(4 + offset)?.trim()
            )
        }
    }

    // --- Network Interfaces via PowerShell ---

    private suspend fun collectNetworkInterfaces(): List<NetworkInterface> = withContext(Dispatchers.IO) {
        val psScript = """
            Get-NetAdapter | Where-Object { ${'$'}_.Status -eq 'Up' } | ForEach-Object {
                ${'$'}adapter = ${'$'}_
                ${'$'}config = Get-NetIPConfiguration -InterfaceIndex ${'$'}adapter.ifIndex -ErrorAction SilentlyContinue
                ${'$'}ips = (${'$'}config.IPv4Address.IPAddress + ${'$'}config.IPv6Address.IPAddress) -join ','
                ${'$'}dns = (${'$'}config.DNSServer.ServerAddresses) -join ','
                ${'$'}gw = (${'$'}config.IPv4DefaultGateway.NextHop) -join ','
                Write-Output "---"
                Write-Output "Name=${'$'}(${'$'}adapter.Name)"
                Write-Output "Description=${'$'}(${'$'}adapter.InterfaceDescription)"
                Write-Output "MAC=${'$'}(${'$'}adapter.MacAddress)"
                Write-Output "Status=${'$'}(${'$'}adapter.Status)"
                Write-Output "IP=${'$'}ips"
                Write-Output "DNS=${'$'}dns"
                Write-Output "Gateway=${'$'}gw"
            }
        """.trimIndent()

        val output = runPowerShell(psScript) ?: return@withContext emptyList()

        output.split("---").filter { it.isNotBlank() }.mapNotNull { block ->
            val props = parseKeyValueOutput(block)
            val name = props["Name"] ?: return@mapNotNull null

            NetworkInterface(
                name = name,
                description = props["Description"],
                macAddress = props["MAC"],
                ipAddresses = props["IP"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                subnet = null,
                gateway = props["Gateway"]?.ifBlank { null },
                dnsServers = props["DNS"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                status = props["Status"] ?: "Up"
            )
        }
    }

    // --- Network Connections via netstat ---

    private suspend fun collectNetworkConnections(): List<NetworkConnection> = withContext(Dispatchers.IO) {
        val output = runCmd("netstat", "-ano") ?: return@withContext emptyList()

        output.lines().mapNotNull { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 4) return@mapNotNull null
            val proto = parts[0]
            if (proto != "TCP" && proto != "UDP") return@mapNotNull null

            val local = parts[1]
            val (localAddr, localPort) = splitAddressPort(local) ?: return@mapNotNull null

            val remote = parts.getOrNull(2)
            val (remoteAddr, remotePort) = if (proto == "TCP" && remote != null) {
                splitAddressPort(remote) ?: (null to null)
            } else null to null

            val state = if (proto == "TCP") parts.getOrNull(3) else null
            val pidStr = if (proto == "TCP") parts.getOrNull(4) else parts.getOrNull(3)
            val pid = pidStr?.toLongOrNull()

            NetworkConnection(
                protocol = proto,
                localAddress = localAddr,
                localPort = localPort,
                remoteAddress = remoteAddr,
                remotePort = remotePort,
                state = state,
                pid = pid,
                processName = null
            )
        }
    }

    // --- Helpers ---

    private fun runPowerShell(script: String): String? {
        return try {
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                logger.warn { "PowerShell timed out" }
                return null
            }
            if (process.exitValue() != 0) {
                logger.debug { "PowerShell exit code ${process.exitValue()}: ${output.take(200)}" }
            }
            output
        } catch (e: Exception) {
            logger.debug { "PowerShell failed: ${e.message}" }
            null
        }
    }

    private fun runCmd(vararg args: String): String? {
        return try {
            val process = ProcessBuilder(args.toList())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(15, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            logger.debug { "Command failed: ${args.joinToString(" ")}: ${e.message}" }
            null
        }
    }

    private fun parseKeyValueOutput(output: String?): Map<String, String> {
        if (output == null) return emptyMap()
        return output.lines()
            .filter { "=" in it }
            .associate { line ->
                val idx = line.indexOf("=")
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .filterValues { it.isNotBlank() }
    }

    private fun splitAddressPort(addressPort: String): Pair<String, Int>? {
        val lastColon = addressPort.lastIndexOf(':')
        if (lastColon < 0) return null
        val addr = addressPort.substring(0, lastColon)
        val port = addressPort.substring(lastColon + 1).toIntOrNull() ?: return null
        return addr to port
    }
}
