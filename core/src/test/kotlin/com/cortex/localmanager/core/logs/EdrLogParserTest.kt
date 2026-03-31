package com.cortex.localmanager.core.logs

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdrLogParserTest {

    private val parser = EdrLogParser()

    @Test
    fun `parseTarArchive extracts events from synthetic tar`() {
        val tempDir = Files.createTempDirectory("edr-test")
        val tarFile = tempDir.resolve("edr-2026-03-11_17-19-21-233.tar")

        // Create a tar with a JSON events file
        val eventsJson = """
            [
              {
                "timestamp": "2026-03-11T17:19:21Z",
                "event_type": "FILE_CREATE",
                "process_image_path": "C:\\Windows\\explorer.exe",
                "pid": 1234,
                "sha256": "abc123def456",
                "file_path": "C:\\Users\\test\\malware.exe",
                "severity": "high"
              },
              {
                "timestamp": "2026-03-11T17:19:22Z",
                "event_type": "NETWORK_CONNECT",
                "process_image_path": "C:\\Windows\\svchost.exe",
                "pid": 5678,
                "dest_ip": "10.0.0.1"
              }
            ]
        """.trimIndent()

        createTar(tarFile.toString(), mapOf("12345-events" to eventsJson))

        val result = parser.parseTarArchive(tarFile)

        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        assertEquals(2, events.size)
        assertEquals("FILE_CREATE", events[0].eventType)
        assertEquals("C:\\Windows\\explorer.exe", events[0].processPath)
        assertEquals(1234L, events[0].pid)
        assertEquals("abc123def456", events[0].sha256)
        assertEquals("high", events[0].severity)
        assertEquals("NETWORK_CONNECT", events[1].eventType)
        assertEquals("10.0.0.1", events[1].networkDestination)

        // Cleanup
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `parseTarArchive handles multiple sub-files`() {
        val tempDir = Files.createTempDirectory("edr-test")
        val tarFile = tempDir.resolve("edr-2026-03-11_17-19-21-233.tar")

        val procJson = """[{"pid": 1234, "process_image_path": "C:\\test.exe"}]"""
        val causJson = """{"timestamp": "2026-03-11T17:19:21Z", "event_type": "CAUSALITY"}"""

        createTar(tarFile.toString(), mapOf(
            "12345-proc" to procJson,
            "12345-caus" to causJson
        ))

        val result = parser.parseTarArchive(tarFile)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `parseJsonFile handles single object`() {
        val tempDir = Files.createTempDirectory("edr-test")
        val jsonFile = tempDir.resolve("single-event.json")
        jsonFile.toFile().writeText("""{"event_type": "PROCESS_START", "pid": 42}""")

        val result = parser.parseJsonFile(jsonFile, "events")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals(42L, result.getOrThrow()[0].pid)

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `parseJsonFile handles empty content`() {
        val tempDir = Files.createTempDirectory("edr-test")
        val jsonFile = tempDir.resolve("empty.json")
        jsonFile.toFile().writeText("")

        val result = parser.parseJsonFile(jsonFile, "events")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())

        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `parser handles all nullable fields gracefully`() {
        val tempDir = Files.createTempDirectory("edr-test")
        val jsonFile = tempDir.resolve("minimal.json")
        jsonFile.toFile().writeText("""[{}]""")

        val result = parser.parseJsonFile(jsonFile, "unknown")
        assertTrue(result.isSuccess)
        val events = result.getOrThrow()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(null, event.timestamp)
        assertEquals(null, event.processPath)
        assertEquals(null, event.pid)
        assertEquals(null, event.sha256)

        tempDir.toFile().deleteRecursively()
    }

    private fun createTar(tarPath: String, files: Map<String, String>) {
        TarArchiveOutputStream(BufferedOutputStream(FileOutputStream(tarPath))).use { tar ->
            for ((name, content) in files) {
                val bytes = content.toByteArray()
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }
}
