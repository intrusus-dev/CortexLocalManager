package com.cortex.localmanager.core.forensics

import com.cortex.localmanager.core.config.AppConfig
import com.cortex.localmanager.core.cytool.CytoolExecutor
import com.cortex.localmanager.core.cytool.CytoolResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val logger = KotlinLogging.logger {}

data class CollectionProgress(
    val currentTask: String = "",
    val completedTasks: Int = 0,
    val totalTasks: Int = 0,
    val artifacts: MutableList<String> = mutableListOf(),
    val errors: MutableList<String> = mutableListOf(),
    val isRunning: Boolean = false,
    val outputPath: Path? = null
)

class ForensicCollector(
    private val cytoolExecutor: CytoolExecutor = CytoolExecutor()
) {
    private val _progress = MutableStateFlow(CollectionProgress())
    val progress: StateFlow<CollectionProgress> = _progress

    /**
     * Collect all forensic artifacts and bundle into a zip file.
     * Requires supervisor password for cytool persist commands.
     */
    suspend fun collectAll(outputDir: Path, password: String): Path = withContext(Dispatchers.IO) {
        val hostname = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "endpoint" }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val zipPath = outputDir.resolve("forensics_${hostname}_$timestamp.zip")

        val tasks = buildTaskList()
        _progress.value = CollectionProgress(totalTasks = tasks.size, isRunning = true)

        val tempDir = Files.createTempDirectory("clm_forensics_")

        try {
            tasks.forEachIndexed { index, task ->
                _progress.value = _progress.value.copy(
                    currentTask = task.name,
                    completedTasks = index
                )
                try {
                    task.execute(tempDir, password)
                    _progress.value.artifacts.add(task.name)
                    logger.info { "Collected: ${task.name}" }
                } catch (e: Exception) {
                    _progress.value.errors.add("${task.name}: ${e.message}")
                    logger.warn { "Failed: ${task.name}: ${e.message}" }
                }
            }

            // Bundle into zip
            _progress.value = _progress.value.copy(currentTask = "Creating zip archive...")
            createZip(tempDir, zipPath)

            _progress.value = _progress.value.copy(
                completedTasks = tasks.size,
                currentTask = "Complete",
                isRunning = false,
                outputPath = zipPath
            )
            logger.info { "Forensic collection complete: $zipPath (${_progress.value.artifacts.size} artifacts)" }
            zipPath
        } finally {
            // Clean up temp directory
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun buildTaskList(): List<ForensicTask> {
        val tasks = mutableListOf<ForensicTask>()

        // Cytool persist databases
        val databases = listOf(
            "security_events.db" to "Security events (alerts & prevention)",
            "hash_ioc.db" to "IoC blacklist entries",
            "wf_verdicts.db" to "WildFire verdict cache",
            "machine_learning_verdicts.db" to "ML verdict cache",
            "yara_verdicts.db" to "YARA verdict cache",
            "hash_paths.db" to "File hash to path mappings",
            "quarantine.db" to "Quarantine records",
            "hash_overrides.db" to "Verdict overrides",
            "agent_settings.db" to "Agent configuration",
            "content_settings.db" to "Content/policy settings",
            "agent_actions.db" to "Pending agent actions"
        )
        for ((db, desc) in databases) {
            tasks.add(CytoolDbTask(db, desc, cytoolExecutor))
        }

        // Cytool status commands (no password needed)
        tasks.add(CytoolCmdTask("agent_info", "Agent info", "info", "query"))
        tasks.add(CytoolCmdTask("protection_status", "Protection status", "protect", "query"))
        tasks.add(CytoolCmdTask("runtime_status", "Runtime components", "runtime", "query"))
        tasks.add(CytoolCmdTask("startup_status", "Startup status", "startup", "query"))
        tasks.add(CytoolCmdTask("last_checkin", "Last check-in", "last_checkin"))

        // Windows artifacts
        tasks.add(PowerShellTask("scheduled_tasks", "Scheduled tasks",
            "Get-ScheduledTask | Where-Object {\$_.State -ne 'Disabled'} | Select-Object TaskName,TaskPath,State,Author | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("services", "Windows services",
            "Get-Service | Select-Object Name,DisplayName,Status,StartType | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("startup_items", "Startup items",
            "Get-CimInstance Win32_StartupCommand | Select-Object Name,Command,Location,User | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("installed_software", "Installed software",
            "Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | Select-Object DisplayName,DisplayVersion,Publisher,InstallDate | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("network_interfaces", "Network interfaces",
            "Get-NetAdapter | Select-Object Name,InterfaceDescription,MacAddress,Status,LinkSpeed | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("network_connections", "Network connections",
            "Get-NetTCPConnection | Select-Object LocalAddress,LocalPort,RemoteAddress,RemotePort,State,OwningProcess | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("dns_cache", "DNS cache",
            "Get-DnsClientCache | Select-Object Entry,RecordName,RecordType,Data,TimeToLive | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("arp_cache", "ARP cache", "arp -a"))
        tasks.add(PowerShellTask("user_sessions", "User sessions", "query user 2>&1"))
        tasks.add(PowerShellTask("processes", "Running processes",
            "Get-Process | Select-Object Id,ProcessName,Path,WorkingSet64,StartTime | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("event_log_security", "Security Event Log (recent 100)",
            "Get-WinEvent -LogName Security -MaxEvents 100 -ErrorAction SilentlyContinue | Select-Object TimeCreated,Id,LevelDisplayName,Message | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("event_log_cortex", "Cortex XDR Event Log",
            "wevtutil qe 'Palo Alto Networks' /rd:true /f:xml /c:200 2>&1"))
        tasks.add(PowerShellTask("prefetch_listing", "Prefetch files",
            "Get-ChildItem C:\\Windows\\Prefetch\\*.pf -ErrorAction SilentlyContinue | Select-Object Name,LastWriteTime,Length | ConvertTo-Csv -NoTypeInformation"))
        tasks.add(PowerShellTask("firewall_rules", "Firewall rules",
            "Get-NetFirewallRule | Where-Object {\$_.Enabled -eq 'True'} | Select-Object Name,DisplayName,Direction,Action,Profile | ConvertTo-Csv -NoTypeInformation"))

        // Copy agent log files
        tasks.add(FileCopyTask("agent_logs", "Agent log files",
            AppConfig.cyveraLogPath.resolve("Logs"), "*.log"))

        return tasks
    }

    private fun createZip(sourceDir: Path, zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { zos ->
            sourceDir.toFile().walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = sourceDir.relativize(file.toPath()).toString()
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }
}

// --- Task implementations ---

private interface ForensicTask {
    val name: String
    val description: String
    fun execute(outputDir: Path, password: String)
}

private class CytoolDbTask(
    private val dbName: String,
    override val description: String,
    private val executor: CytoolExecutor
) : ForensicTask {
    override val name = "cytool_$dbName"

    override fun execute(outputDir: Path, password: String) {
        val result = runBlocking(executor, "persist", "print", dbName, password = password)
        val subDir = outputDir.resolve("cytool_databases")
        Files.createDirectories(subDir)
        subDir.resolve(dbName.replace(".db", ".json")).toFile().writeText(result)
    }

    private fun runBlocking(executor: CytoolExecutor, vararg args: String, password: String): String {
        val command = listOf(AppConfig.cytoolPath.toString()) + args.toList()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { w -> w.write(password); w.newLine(); w.flush() }
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(120, TimeUnit.SECONDS)
        return output
    }
}

private class CytoolCmdTask(
    private val fileName: String,
    override val description: String,
    private vararg val args: String
) : ForensicTask {
    override val name = "cytool_$fileName"

    override fun execute(outputDir: Path, password: String) {
        val command = listOf(AppConfig.cytoolPath.toString()) + args.toList()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        val subDir = outputDir.resolve("cytool_status")
        Files.createDirectories(subDir)
        subDir.resolve("$fileName.txt").toFile().writeText(output)
    }
}

private class PowerShellTask(
    private val fileName: String,
    override val description: String,
    private val script: String
) : ForensicTask {
    override val name = "windows_$fileName"

    override fun execute(outputDir: Path, password: String) {
        val process = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        val subDir = outputDir.resolve("windows_artifacts")
        Files.createDirectories(subDir)
        val ext = if (output.contains(",") && output.lines().size > 1) "csv" else "txt"
        subDir.resolve("$fileName.$ext").toFile().writeText(output)
    }
}

private class FileCopyTask(
    private val dirName: String,
    override val description: String,
    private val sourceDir: Path,
    private val pattern: String
) : ForensicTask {
    override val name = "files_$dirName"

    override fun execute(outputDir: Path, password: String) {
        if (!Files.exists(sourceDir)) return
        val destDir = outputDir.resolve(dirName)
        Files.createDirectories(destDir)
        sourceDir.toFile().listFiles()?.filter { it.name.matches(Regex(pattern.replace("*", ".*"))) }
            ?.take(50) // limit to 50 files
            ?.forEach { file ->
                try {
                    file.copyTo(destDir.resolve(file.name).toFile(), overwrite = true)
                } catch (_: Exception) {}
            }
    }
}
