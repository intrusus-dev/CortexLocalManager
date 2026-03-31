package com.cortex.localmanager.core.logs

import com.cortex.localmanager.core.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.file.*

private val logger = KotlinLogging.logger {}

sealed class LogEvent {
    data class NewEdrLog(val path: Path, val events: List<EdrEvent>) : LogEvent()
    data class NewPreventionAlert(val path: Path, val alert: PreventionAlert) : LogEvent()
    data class ParseError(val path: Path, val error: String) : LogEvent()
}

class LogWatcher(
    private val logDirectory: Path = AppConfig.cyveraLogPath,
    private val edrLogParser: EdrLogParser = EdrLogParser(),
    private val preventionLogParser: PreventionLogParser = PreventionLogParser(),
    private val scope: CoroutineScope,
    private val debounceMs: Long = 500
) {
    private val _events = MutableSharedFlow<LogEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<LogEvent> = _events

    private var watchJob: Job? = null

    fun start(): Job {
        watchJob?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            logger.info { "Starting log watcher on $logDirectory" }

            if (!Files.exists(logDirectory)) {
                logger.warn { "Log directory does not exist: $logDirectory" }
                return@launch
            }

            val watchService = logDirectory.fileSystem.newWatchService()
            logDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)

            try {
                while (isActive) {
                    val key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS) ?: continue
                    val events = key.pollEvents()

                    for (event in events) {
                        @Suppress("UNCHECKED_CAST")
                        val ev = event as WatchEvent<Path>
                        val filePath = logDirectory.resolve(ev.context())

                        // Debounce rapid writes
                        delay(debounceMs)

                        processNewFile(filePath)
                    }

                    if (!key.reset()) {
                        logger.warn { "Watch key invalidated, stopping watcher" }
                        break
                    }
                }
            } finally {
                watchService.close()
                logger.info { "Log watcher stopped" }
            }
        }
        watchJob = job
        return job
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    private suspend fun processNewFile(path: Path) {
        val fileName = path.fileName.toString().lowercase()

        when {
            fileName.startsWith("edr-") && fileName.endsWith(".tar") -> {
                edrLogParser.parseTarArchive(path)
                    .onSuccess { events ->
                        if (events.isNotEmpty()) {
                            _events.emit(LogEvent.NewEdrLog(path, events))
                        }
                    }
                    .onFailure { e ->
                        _events.emit(LogEvent.ParseError(path, e.message ?: "EDR parse error"))
                    }
            }

            fileName.endsWith(".xml") -> {
                preventionLogParser.parseXmlFile(path)
                    .onSuccess { alert ->
                        _events.emit(LogEvent.NewPreventionAlert(path, alert))
                    }
                    .onFailure { e ->
                        _events.emit(LogEvent.ParseError(path, e.message ?: "Prevention XML parse error"))
                    }
            }
        }
    }
}
