package com.cortex.localmanager.core.config

import java.nio.file.Path
import java.nio.file.Paths

object AppConfig {
    val cytoolPath: Path = Paths.get(
        System.getenv("CYTOOL_PATH")
            ?: "C:\\Program Files\\Palo Alto Networks\\Traps\\cytool.exe"
    )

    val cyveraLogPath: Path = Paths.get(
        System.getenv("CYVERA_LOG_PATH")
            ?: "C:\\ProgramData\\Cyvera"
    )

    val appDataPath: Path = Paths.get(
        System.getenv("CLM_DATA_PATH")
            ?: System.getProperty("user.home")
    ).resolve(".cortex-local-manager")

    val suexPath: Path = appDataPath.resolve("suex")

    val refreshIntervalMs: Long = 5_000
    val logPollIntervalMs: Long = 3_000
}
