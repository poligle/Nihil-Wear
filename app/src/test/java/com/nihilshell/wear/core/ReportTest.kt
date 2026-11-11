package com.nihilshell.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportTest {
    @Test
    fun takesTheMiddle() {
        assertNull(median(emptyList()))
        assertEquals(7L, median(listOf(7L)))
        assertEquals(20L, median(listOf(90L, 10L, 20L)))
        assertEquals(15L, median(listOf(20L, 10L)))
    }

    @Test
    fun aWaySumsAFirstTap() {
        val way = Way("bluetooth", rounds = listOf(Round(30, 60, 20, 9), Round(40, 70, 30, 11), Round(35, 65, 25, 10)))
        assertEquals(125L, way.cold)
        assertEquals(10L, way.warm)
        assertNull(Way("wifi", error = "unavailable").cold)
    }

    @Test
    fun quotesWhatItWrites() {
        assertEquals("null", Json.quote(null))
        assertEquals("\"Watch (\\\"4F2A\\\")\"", Json.quote("Watch (\"4F2A\")"))
        assertEquals("\"a\\\\b\\nc\\u0001\"", Json.quote("a\\b\nc\u0001"))
    }

    @Test
    fun writesAReportToolsWatchCanRead() {
        val report = Report(
            run = 3,
            state = "done",
            at = 1000L,
            device = "Watch",
            sdk = 36,
            computer = "nihil",
            pairing = "paired",
            default = Way("bluetooth", null, "192.168.1.20", listOf(Round(30, 60, 20, 9)), 95, null),
            wifi = Way("wifi", error = "unavailable"),
            mdns = Mdns("all", error = "not-found"),
            rotary = 4,
        )
        assertEquals(
            """{"run":3,"state":"done","at":1000,"device":"Watch","sdk":36,"computer":"nihil","pairing":"paired",""" +
                """"default":{"network":"bluetooth","waited":null,"address":"192.168.1.20","cold":110,"warm":9,"state":95,"error":null,""" +
                """"rounds":[{"connect":30,"handshake":60,"hello":20,"again":9}]},""" +
                """"wifi":{"network":"wifi","waited":null,"address":null,"cold":null,"warm":null,"state":null,"error":"unavailable","rounds":[]},""" +
                """"mdns":{"network":"all","found":null,"address":null,"error":"not-found"},"rotary":4}""",
            report.json(),
        )
    }
}
