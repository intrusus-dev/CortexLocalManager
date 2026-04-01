package com.cortex.localmanager.core.cytool

import com.cortex.localmanager.core.config.AppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

class CytoolExecutor(
    private val cytoolPath: String = AppConfig.cytoolPath.toString(),
    private val defaultTimeoutSeconds: Long = 30
) {

    suspend fun execute(
        vararg args: String,
        password: String? = null,
        timeoutSeconds: Long = defaultTimeoutSeconds
    ): CytoolResult<String> = withContext(Dispatchers.IO) {
        val command = listOf(cytoolPath) + args.toList()
        val commandString = command.joinToString(" ")
        logger.debug { "Executing: $commandString" }

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            if (password != null) {
                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(password)
                    writer.newLine()
                    writer.flush()
                }
            }

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                logger.warn { "Command timed out after ${timeoutSeconds}s: $commandString" }
                return@withContext CytoolResult.Timeout(commandString, timeoutSeconds)
            }

            val exitCode = process.exitValue()
            logger.debug { "Exit code $exitCode for: $commandString" }

            val errorPattern = detectError(output, exitCode)
            if (errorPattern != null) {
                CytoolResult.Error(errorPattern, output, exitCode)
            } else {
                CytoolResult.Success(output, output)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute: $commandString" }
            CytoolResult.Error(
                message = e.message ?: "Unknown error executing cytool",
                rawOutput = "",
                exitCode = -1
            )
        }
    }

    private fun detectError(output: String, exitCode: Int): String? {
        if (exitCode != 0) {
            return "cytool exited with code $exitCode"
        }

        val errorPatterns: List<Pair<String, String>> = listOf(
            "Access denied" to "Access denied — check supervisor password",
            "Failed to" to "Operation failed",
            "Error:" to (output.lines().firstOrNull { it.contains("Error:") }?.trim() ?: "Unknown error"),
            "is not recognized" to "cytool not found at configured path"
        )

        for ((pattern, message) in errorPatterns) {
            if (output.contains(pattern, ignoreCase = true)) {
                return message
            }
        }

        return null
    }
}
