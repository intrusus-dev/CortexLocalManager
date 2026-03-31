package com.cortex.localmanager.core.cytool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CytoolOutputParserTest {

    // --- parseProtectQuery ---

    @Test
    fun `parseProtectQuery parses standard protection output`() {
        val output = """
            Protection      Mode            State
            Process         Policy          Enabled
            Registry        Policy          Enabled
            File            Policy          Enabled
            Service         Policy          Enabled
            Pipe            Policy          Enabled
        """.trimIndent()

        val features = CytoolOutputParser.parseProtectQuery(output)

        assertEquals(5, features.size)
        assertEquals("Process", features[0].name)
        assertEquals("Policy", features[0].mode)
        assertTrue(features[0].enabled)
        assertEquals("Pipe", features[4].name)
        assertTrue(features[4].enabled)
    }

    @Test
    fun `parseProtectQuery handles disabled features`() {
        val output = """
            Protection      Mode            State
            Process         Policy          Enabled
            Registry        Manual          Disabled
        """.trimIndent()

        val features = CytoolOutputParser.parseProtectQuery(output)

        assertEquals(2, features.size)
        assertTrue(features[0].enabled)
        assertEquals("Manual", features[1].mode)
        assertEquals(false, features[1].enabled)
    }

    @Test
    fun `parseProtectQuery handles empty output`() {
        val features = CytoolOutputParser.parseProtectQuery("")
        assertTrue(features.isEmpty())
    }

    // --- parseInfoQuery ---

    @Test
    fun `parseInfoQuery parses standard info output`() {
        val output = """
            Content Type: 2200
            Content Build: 33468
            Content Version: 2200-33468
            Event Log: 1
        """.trimIndent()

        val info = CytoolOutputParser.parseInfoQuery(output)

        assertNotNull(info)
        assertEquals(2200, info.contentType)
        assertEquals(33468, info.contentBuild)
        assertEquals("2200-33468", info.contentVersion)
        assertEquals(1, info.eventLog)
    }

    @Test
    fun `parseInfoQuery returns null for malformed output`() {
        val info = CytoolOutputParser.parseInfoQuery("some garbage output")
        assertNull(info)
    }

    @Test
    fun `parseInfoQuery returns null for missing required fields`() {
        val output = """
            Content Type: 2200
            Event Log: 1
        """.trimIndent()

        val info = CytoolOutputParser.parseInfoQuery(output)
        assertNull(info)
    }

    // --- parseFileSearch ---

    @Test
    fun `parseFileSearch parses single result`() {
        val output = """
            SHA256: 03be52cacf1d172decdfec06f8f770c590bf84df6fdebdb3520ceec4d966f779
            MD5: 2da60e17e855310bc2f57bba3bf560bfc
            File ID: 1970324837135989
            File Path: C:\Users\lab-user\AppData\Local\Google\Chrome\User Data\Default\Cache\123
            Date Created: 2026-03-11T20:15:22
            Date Last Modified: 2026-03-11T20:15:29
            Created By User: BYOS\lab-user
            Created By SID: S-1-5-21-567966250-1234567890-1234567890-1001
        """.trimIndent()

        val results = CytoolOutputParser.parseFileSearch(output)

        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("03be52cacf1d172decdfec06f8f770c590bf84df6fdebdb3520ceec4d966f779", r.sha256)
        assertEquals("2da60e17e855310bc2f57bba3bf560bfc", r.md5)
        assertEquals("1970324837135989", r.fileId)
        assertEquals("C:\\Users\\lab-user\\AppData\\Local\\Google\\Chrome\\User Data\\Default\\Cache\\123", r.filePath)
        assertEquals("2026-03-11T20:15:22", r.dateCreated)
        assertEquals("BYOS\\lab-user", r.createdByUser)
        assertTrue(r.found)
    }

    @Test
    fun `parseFileSearch parses multiple results for same hash`() {
        val output = """
            SHA256: 79c185e4cbff2ea12ad30fcb1a8a4383945e4928
            MD5: 2da60e17e855310bc2f57bba3bf560bfc
            File Path: C:\Users\lab-user\AppData\Local\Google\Chrome\cache
            Date Created: 2026-03-11T20:15:22

            SHA256: 79c185e4cbff2ea12ad30fcb1a8a4383945e4928
            MD5: 2da60e17e855310bc2f57bba3bf560bfc
            File Path: C:\Users\lab-user\Downloads\wildfire-test.exe
            Date Created: 2026-03-11T20:15:29
        """.trimIndent()

        val results = CytoolOutputParser.parseFileSearch(output)

        assertEquals(2, results.size)
        assertTrue(results[0].filePath.contains("Chrome"))
        assertTrue(results[1].filePath.contains("Downloads"))
    }

    @Test
    fun `parseFileSearch returns empty list when not found`() {
        val output = "Hash not found in local database"
        val results = CytoolOutputParser.parseFileSearch(output)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `parseFileSearch returns empty list for blank output`() {
        assertTrue(CytoolOutputParser.parseFileSearch("").isEmpty())
    }

    // --- extractJsonFromPersist ---

    @Test
    fun `extractJsonFromPersist extracts JSON array`() {
        val output = """
            Database (identified by name) is in use by the agent - attempting to access the DB via RPC.
            Enter supervisor password:
            [{"key":"abc","value":{"sha256":"def"}}]
            Iterated [1] entries.
        """.trimIndent()

        val json = CytoolOutputParser.extractJsonFromPersist(output)

        assertNotNull(json)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"key\":\"abc\""))
    }

    @Test
    fun `extractJsonFromPersist handles empty array`() {
        val output = """
            Database (identified by name) is in use by the agent - attempting to access the DB via RPC.
            Enter supervisor password:
            []
            Iterated [0] entries.
        """.trimIndent()

        val json = CytoolOutputParser.extractJsonFromPersist(output)

        assertNotNull(json)
        assertEquals("[]", json)
    }

    @Test
    fun `extractJsonFromPersist returns null when no JSON present`() {
        val output = "Some error message with no JSON"
        assertNull(CytoolOutputParser.extractJsonFromPersist(output))
    }

    @Test
    fun `extractJsonFromPersist handles multiline JSON`() {
        val output = """
            Enter supervisor password:
            [
              {"key":"1","value":{"sha256":"aaa"}},
              {"key":"2","value":{"sha256":"bbb"}}
            ]
            Iterated [2] entries.
        """.trimIndent()

        val json = CytoolOutputParser.extractJsonFromPersist(output)

        assertNotNull(json)
        assertTrue(json.contains("\"key\":\"1\""))
        assertTrue(json.contains("\"key\":\"2\""))
    }
}
