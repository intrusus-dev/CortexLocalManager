package com.cortex.localmanager.core.logs

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreventionLogParserTest {

    private val parser = PreventionLogParser()

    @Test
    fun `parseXmlString extracts all fields from prevention XML`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <PreventionAlert>
                <Suspicious Mode="Process" Pid="7764" Block="True" />
                <Date>Wednesday, March 11, 2026</Date>
                <Time>20:15:29</Time>
                <ComponentName>LocalAnalysis</ComponentName>
                <OperationStatus>blocked</OperationStatus>
                <Description>Suspicious executable detected</Description>
                <ProcessPath>C:\Users\lab-user\Downloads\malware.exe</ProcessPath>
                <UserName>BYOS\lab-user</UserName>
                <OSVersion>10.0.19045</OSVersion>
                <AdditionalArguments>SHA256=03be52cacf1d172decdfec06f8f770c590bf84df6fdebdb3520ceec4d966f779 MD5=2da60e17e855310bc2f57bba3bf560bfc FilePath=C:\Users\lab-user\Downloads\malware.exe SID=S-1-5-21-567966250</AdditionalArguments>
            </PreventionAlert>
        """.trimIndent()

        val alert = parser.parseXmlString(xml, "test.xml")

        assertEquals(7764L, alert.pid)
        assertTrue(alert.blocked)
        assertEquals("Process", alert.trigger)
        assertEquals("LocalAnalysis", alert.componentName)
        assertEquals("blocked", alert.operationStatus)
        assertEquals("Suspicious executable detected", alert.description)
        assertEquals("C:\\Users\\lab-user\\Downloads\\malware.exe", alert.processPath)
        assertEquals("BYOS\\lab-user", alert.user)
        assertEquals("10.0.19045", alert.osVersion)
        assertEquals("03be52cacf1d172decdfec06f8f770c590bf84df6fdebdb3520ceec4d966f779", alert.sha256)
        assertEquals("2da60e17e855310bc2f57bba3bf560bfc", alert.md5)
        assertEquals("S-1-5-21-567966250", alert.userSid)
        assertEquals("test.xml", alert.sourceFile)
        // Timestamp should be valid (not DISTANT_PAST)
        assertTrue(alert.timestamp > Instant.DISTANT_PAST)
    }

    @Test
    fun `parseXmlString handles Block=False`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <PreventionAlert>
                <Suspicious Mode="Process" Pid="100" Block="False" />
                <Date>Wednesday, March 11, 2026</Date>
                <Time>20:15:29</Time>
                <ComponentName>BehavioralThreat</ComponentName>
                <OperationStatus>reported</OperationStatus>
                <Description>Suspicious behavior</Description>
            </PreventionAlert>
        """.trimIndent()

        val alert = parser.parseXmlString(xml)

        assertEquals(false, alert.blocked)
        assertEquals("reported", alert.operationStatus)
        assertEquals("BehavioralThreat", alert.componentName)
    }

    @Test
    fun `parseXmlString handles missing optional fields`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <PreventionAlert>
                <Date>Wednesday, March 11, 2026</Date>
                <Time>20:15:29</Time>
                <Description>Minimal alert</Description>
            </PreventionAlert>
        """.trimIndent()

        val alert = parser.parseXmlString(xml)

        assertEquals(null, alert.pid)
        assertEquals(false, alert.blocked)
        assertEquals(null, alert.sha256)
        assertEquals(null, alert.processPath)
        assertEquals("Minimal alert", alert.description)
    }

    @Test
    fun `parseDateTime parses known date format`() {
        val instant = PreventionLogParser.parseDateTime(
            "Wednesday, March 11, 2026",
            "20:15:29"
        )

        assertNotNull(instant)
        assertTrue(instant > Instant.DISTANT_PAST)
    }

    @Test
    fun `parseDateTime returns DISTANT_PAST for null input`() {
        assertEquals(Instant.DISTANT_PAST, PreventionLogParser.parseDateTime(null, null))
        assertEquals(Instant.DISTANT_PAST, PreventionLogParser.parseDateTime("Wednesday, March 11, 2026", null))
        assertEquals(Instant.DISTANT_PAST, PreventionLogParser.parseDateTime(null, "20:15:29"))
    }

    @Test
    fun `parseDateTime returns DISTANT_PAST for malformed input`() {
        assertEquals(Instant.DISTANT_PAST, PreventionLogParser.parseDateTime("garbage", "20:15:29"))
        assertEquals(Instant.DISTANT_PAST, PreventionLogParser.parseDateTime("Wednesday, March 11, 2026", "bad"))
    }
}
