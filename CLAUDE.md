# Cortex Local Manager — Emergency Endpoint Management Prototype

## Project Overview

A Kotlin/JVM + Compose Desktop application that provides local, offline management of Palo Alto Networks Cortex XDR agents when the Cortex Cloud Gateway and XDR Tenant are unavailable. Built as a prototype for a banking customer with 300k endpoints who requires operational sovereignty (on-premise emergency management capability).

**This is NOT a replacement for the cloud console.** It is an emergency/basis mode tool that wraps the existing `cytool.exe` CLI, reads local agent logs, and manages SUEX exception files — giving SOC analysts continued visibility and control when disconnected from the cloud.

## Tech Stack

- **Language:** Kotlin 2.0+ (JVM target 17)
- **UI Framework:** Compose Multiplatform for Desktop (Material 3)
- **Build System:** Gradle with Kotlin DSL (`build.gradle.kts`)
- **Serialization:** `kotlinx.serialization` (JSON)
- **Async:** `kotlinx.coroutines`
- **Windows APIs:** JNA (`net.java.dev.jna`) for Windows Event Log access
- **File Watching:** `java.nio.file.WatchService`
- **Packaging:** `jpackage` (bundled JDK 17+) → Windows `.exe`/`.msi`
- **IDE:** IntelliJ IDEA

## Architecture

```
cortex-local-manager/
├── core/                          # Business logic module
│   ├── cytool/                    # Cytool CLI wrapper & output parser
│   │   ├── CytoolExecutor.kt     # ProcessBuilder wrapper, stdin password piping
│   │   ├── CytoolCommands.kt     # Typed command builders for each cytool subcommand
│   │   └── CytoolOutputParser.kt # JSON + text output deserialization
│   ├── logs/                      # Local agent log parsers
│   │   ├── EdrLogParser.kt       # Parse EDR JSON from c:\programdata\Cyvera\
│   │   ├── PreventionLogParser.kt # Parse prevention XML files
│   │   ├── LogWatcher.kt         # WatchService-based real-time log monitor
│   │   └── LogRepository.kt      # Aggregates all log sources into UnifiedAlert
│   ├── suex/                      # SUEX exception config management
│   │   ├── SuexModels.kt         # Data classes for SUEX JSON schema
│   │   └── SuexManager.kt        # Create, load, save, validate SUEX files
│   ├── models/                    # Shared data classes
│   │   ├── AgentInfo.kt
│   │   ├── ProtectionStatus.kt
│   │   ├── HashEntry.kt
│   │   ├── UnifiedAlert.kt
│   │   └── FileSearchResult.kt
│   └── config/
│       └── AppConfig.kt
├── ui/                            # Compose Desktop UI module
│   ├── App.kt                    # Main application entry, navigation
│   ├── navigation/                # Sidebar, screen enum
│   ├── dashboard/                 # Agent status overview + recent alerts
│   ├── detections/                # Prevention logs, EDR events, telemetry viewer
│   ├── hunting/                   # Hash search, DB refresh, hash browser
│   ├── exceptions/                # SUEX exception editor
│   ├── theme/                     # Dark professional styling
│   └── components/                # Shared UI: DataTable, StatusBadge, HashText, etc.
├── build.gradle.kts
├── settings.gradle.kts
├── CLAUDE.md                      # This file
└── tasks/                         # Claude Code task files
```

## Core Domain Knowledge

### Cytool CLI

Cytool (`C:\Program Files\Palo Alto Networks\Traps\cytool.exe`) is a command-line tool integrated into the Cortex XDR agent. Most commands require a **supervisor password** piped via stdin.

**Password piping pattern (critical):**
```kotlin
val process = ProcessBuilder(
    "C:\\Program Files\\Palo Alto Networks\\Traps\\cytool.exe",
    *args.toTypedArray()
).redirectErrorStream(true).start()

process.outputStream.bufferedWriter().use {
    it.write(supervisorPassword)
    it.newLine()
    it.flush()
}
val output = process.inputStream.bufferedReader().readText()
val exitCode = process.waitFor()
```

**Key cytool subcommands:**

| Command | Description | Output Format |
|---------|-------------|---------------|
| `protect query` | Show protection feature states | Text table: `Protection Mode State` |
| `quarantine list` | List quarantined files | JSON array (requires password) |
| `quarantine restore <id>` | Restore a quarantined file | Text confirmation |
| `quarantine delete <id>` | Delete a quarantined file | Text confirmation |
| `info query` | Agent info (content version, etc.) | Text key-value pairs |
| `scan start` | Initiate a scan | Text confirmation |
| `file_search hash <sha256>` | Search for file hash in local DB | Text with file properties |
| `file_system_scan start` | Refresh local XDR hash database | Text confirmation |
| `file_system_scan query` | Monitor refresh progress | Text status |
| `persist list` | List all local databases | Text list |
| `persist print <db_name>` | Export DB contents as JSON | JSON array (requires password) |
| `persist export <db_name>` | Export DB to file | File output |
| `persist import <db_name>` | Import DB from file | Text confirmation |
| `stat query` | Agent process statistics | Text stats |
| `startup query` | Agent component startup states | Text table |
| `runtime query` | Running component status | Text status |
| `isolate release` | Release from network isolation | Text confirmation |
| `edr` | Run EDR operations | Various |
| `event_collection start/stop` | Control EDR/DSE collection | Text confirmation |
| `log set <process> <level>` | Set log level | Text confirmation |
| `checkin` | Force server check-in | Text confirmation |
| `last_checkin` | Show last successful check-in time | Text timestamp |

### Cytool Persist Databases

There are ~34 encrypted local databases. Key ones for emergency management:

- `security_events.db` — Detection/alert feed
- `hash_ioc.db` — IoC blacklist entries
- `hash_overrides.db` — Verdict overrides (for IR)
- `quarantine.db` — Quarantine state
- `agent_settings.db` — Agent configuration
- `content_settings.db` — Content/policy settings
- `wf_verdicts.db` — WildFire verdict cache
- `machine_learning_verdicts.db` — ML verdict cache
- `agent_actions.db` — Pending agent actions
- `hash_paths.db` — File hash to path mappings
- `dse_settings.db` — DSE (Data Security Engine) settings
- `file_id_hash.db` — File ID to SHA256 mappings

All encrypted DBs must be accessed via `cytool persist print/export/import` — cannot read from disk directly.

**Known JSON schema for `file_id_hash.db`:**
```json
{
  "key": "1970324837135989,{574d521a-09ce-43e7-8bb9-4af9b4c261b9}",
  "value": {
    "lruData": { "lastUsed": "1774799593", "index": "14" },
    "sha256": "03be52cacf1d172decdfec06f8f770c590bf84df6fdebdb3520ceec4d966f779"
  }
}
```

**Known `agent_actions.db` schema:** Empty array `[]` when no pending actions.

### SUEX (Support Exceptions)

SUEX files are JSON configs for exception/allowlisting management. They are **not** for prevention rules or policy updates — only for exclusions.

**Standard SUEX structure:**
```json
{
  "data": {
    "condition": "true",
    "rules": {
      "DisableInjection": [
        {
          "action": "set",
          "processes": ["C:\\Windows\\notepad.exe"],
          "settings": true
        }
      ]
    }
  },
  "metadata": {
    "name": "TEST SUEX",
    "os_type": 1,
    "profileType": 1
  }
}
```

There are many SUEX rule types beyond `DisableInjection`. The prototype should support common exception categories. SUEX files are imported via the Cortex XDR UI or cytool.

### Local Agent Logs

**Location:** `c:\programdata\Cyvera\`

**EDR logs:** JSON files named like `edr-2026-02-10_17-19-21-233.tar` containing:
- `*-caus...` — Causality/correlation data
- `*-events` — Event data
- `*-cont...` — Context data
- `*-mgei...` — Management event info
- `*-proc...` — Process information
- `*-ep_p...` — Endpoint process data

**Prevention logs:** XML files in ProgramData, also mirrored to Windows Event Log under "Palo Alto Networks" source. Prevention alerts contain SHA256 hashes, file paths, timestamps, user context, and SID.

### Agent Info Output

```
Content Type: 2200
Content Build: 33468
Content Version: 2200-33468
Event Log: 1
```

### Protection Status Output

```
Protection      Mode            State
Process         Policy          Enabled
Registry        Policy          Enabled
File            Policy          Enabled
Service         Policy          Enabled
Pipe            Policy          Enabled
```

## Key Design Decisions

1. **No Electron/web stack** — Bank won't accept Chromium on 300k endpoints. Compose Desktop produces ~50-80MB self-contained exe via jpackage.
2. **No Python** — Dependency management nightmare at scale on Windows.
3. **Supervisor password stored in memory only** — User enters once per session, held in `SecureString`-equivalent, never persisted to disk.
4. **All cytool interactions are async** — Use `kotlinx.coroutines` with `Dispatchers.IO` to prevent UI freezing.
5. **Professional/banking-appropriate UI** — Dark theme, clean typography, no playful elements. Think Bloomberg terminal aesthetics.
6. **Windows-only** — Target platform is Windows (latest XDR agent version).
7. **Single-endpoint tool** — This runs locally on each endpoint. Aggregation across endpoints is handled by Elastic Beat/Splunk Universal Forwarder (out of scope for v1).

## Coding Conventions

- Use Kotlin idioms: data classes, sealed classes for result types, extension functions
- All cytool operations return `Result<T>` or a sealed class like `CytoolResult.Success / CytoolResult.Error`
- JSON parsing via `@Serializable` data classes with `kotlinx.serialization`
- Compose UI uses `ViewModel`-style state holders with `StateFlow`
- Coroutines: `viewModelScope` for UI-bound work, structured concurrency everywhere
- No hardcoded paths — use a `Config` object with defaults that can be overridden
- Comprehensive error handling — cytool can fail in many ways (wrong password, agent not running, DB locked, permission denied)
- Log operations internally using `kotlin.logging` or `slf4j`

## Testing Strategy

- Unit tests for all parsers (cytool output, EDR JSON, prevention XML, SUEX JSON)
- Integration tests with mock cytool output (recorded from real agent)
- UI preview tests using Compose Desktop preview
- Manual testing on Parallels Windows VM with live Cortex XDR agent

## Task Execution Order

Execute tasks from `tasks/` in this order — each builds on the previous:

1. **01_project_scaffold.md** — Gradle project, modules, dependencies
2. **02_cytool_executor.md** — CytoolExecutor, CytoolCommands, OutputParser, Models
3. **03_log_parsers.md** — EDR/Prevention parsers, LogWatcher, LogRepository
4. **04_ui_shell.md** — Theme, sidebar navigation, password entry, shared components
5. **05_dashboard.md** — Agent status cards + recent alerts feed
6. **06_detections_viewer.md** — Full alert table with filtering + detail panel
7. **07_threat_hunting.md** — Hash search, DB refresh, hash browser
8. **08_suex_exceptions.md** — SUEX exception editor (visual + JSON mode)

Tasks 01-03 are backend/core. Tasks 04-08 are UI. Task 04 must come before 05-08 since they plug into the shell.

## Important Notes

- Cytool changes persist until the next heartbeat from Cortex XDR cloud (~5 min). If the cloud is unreachable, changes persist indefinitely — which is the desired behavior for this emergency tool.
- Emergency Management ≠ Endpoint Protection. The agent continues protecting in offline mode with ML analytics and offline engine functionalities. This tool manages the agent, not replaces its protection.
- The `content.zip` import (`cytool import content`) is for offline content/signature updates. Need to verify whether the agent validates the zip signature against the cloud.