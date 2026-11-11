package com.nihilshell.wear.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class HttpTest {
    @Test
    fun writesARequestTheDeckReads() {
        val bytes = Http.request("POST", "/v1/pair", "192.168.1.20", 64445, null, """{"code":"ABCDEFGH"}""")
        assertEquals(
            "POST /v1/pair HTTP/1.1\r\nHost: 192.168.1.20:64445\r\nConnection: keep-alive\r\n" +
                "Content-Type: application/json\r\nContent-Length: 19\r\n\r\n{\"code\":\"ABCDEFGH\"}",
            String(bytes, Charsets.UTF_8),
        )
    }

    @Test
    fun sendsTheToken() {
        val head = String(Http.request("GET", "/v1/state", "10.0.0.2", 64445, "secret"), Charsets.US_ASCII)
        assertTrue(head.contains("Authorization: Bearer secret\r\n"))
        assertTrue(head.endsWith("\r\n\r\n"))
    }

    @Test
    fun readsTwoAnswersOnOneConnection() {
        val stream = ByteArrayInputStream(
            ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 28\r\n\r\n" +
                "{\"deck\": 1, \"name\": \"nihil\"}" +
                "HTTP/1.1 401 Unauthorized\r\ncontent-length: 21\r\nConnection: close\r\n\r\n{\"error\": \"unpaired\"}").toByteArray(),
        )
        val first = Http.read(stream)
        assertEquals(200, first.status)
        assertEquals("application/json", first.headers["content-type"])
        assertEquals("{\"deck\": 1, \"name\": \"nihil\"}", first.text)
        val second = Http.read(stream)
        assertEquals(401, second.status)
        assertEquals("{\"error\": \"unpaired\"}", second.text)
        assertEquals("close", second.headers["connection"])
    }

    @Test
    fun readsABodyThatComesInPieces() {
        val whole = "HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\nhello there".toByteArray()
        val trickle = object : InputStream() {
            private var at = 0

            override fun read(): Int = if (at < whole.size) whole[at++].toInt() and 0xFF else -1

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= whole.size) return -1
                b[off] = whole[at++]
                return 1
            }
        }
        assertEquals("hello there", Http.read(trickle).text)
    }

    @Test(expected = IOException::class)
    fun failsWhenTheConnectionCloses() {
        Http.read(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 50\r\n\r\nshort".toByteArray()))
    }

    @Test(expected = IOException::class)
    fun refusesWhatIsntHttp() {
        Http.read(ByteArrayInputStream("SSH-2.0-OpenSSH\r\n\r\n".toByteArray()))
    }

    @Test(expected = IOException::class)
    fun refusesAnEnormousBody() {
        Http.read(ByteArrayInputStream("HTTP/1.1 200 OK\r\nContent-Length: 999999999\r\n\r\n".toByteArray()))
    }
}
