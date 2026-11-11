package com.nihilshell.wear.core

import java.io.IOException
import java.io.InputStream

/**
 * HTTP/1.1 as the deck speaks it (docs/deck.md in Nihil-Shell, Requests): one
 * request at a time on a connection kept open, JSON bodies with their
 * Content-Length. Written by hand so the test can time each step on one
 * connection. Pure Kotlin, tested on the JVM.
 */
object Http {
    class Response(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    /** A request's bytes: its head and, when there is one, its JSON body. */
    fun request(method: String, path: String, host: String, port: Int, token: String? = null, body: String? = null): ByteArray {
        val content = body?.toByteArray(Charsets.UTF_8)
        val head = StringBuilder()
            .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            .append("Host: ").append(if (host.contains(':')) "[$host]" else host).append(':').append(port).append("\r\n")
            .append("Connection: keep-alive\r\n")
        if (token != null) head.append("Authorization: Bearer ").append(token).append("\r\n")
        if (content != null) head.append("Content-Type: application/json\r\nContent-Length: ").append(content.size).append("\r\n")
        head.append("\r\n")
        val bytes = head.toString().toByteArray(Charsets.US_ASCII)
        return if (content == null) bytes else bytes + content
    }

    /**
     * One response from [input]: its head a byte at a time, then exactly its
     * Content-Length, so the next response on the same connection starts
     * where this one ends.
     */
    fun read(input: InputStream, limit: Int = 65536): Response {
        val lines = ArrayList<String>()
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) throw IOException("closed")
            if (c == '\n'.code) {
                val text = line.toString().trimEnd('\r')
                line.setLength(0)
                if (text.isEmpty()) break
                lines += text
                if (lines.size > 64) throw IOException("head too long")
            } else {
                line.append(c.toChar())
                if (line.length > 8192) throw IOException("head too long")
            }
        }
        val first = lines.firstOrNull() ?: throw IOException("not HTTP")
        if (!first.startsWith("HTTP/1.")) throw IOException("not HTTP")
        val status = first.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("not HTTP")
        val headers = HashMap<String, String>()
        for (header in lines.drop(1)) {
            val colon = header.indexOf(':')
            if (colon > 0) headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > limit) throw IOException("body too long")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) throw IOException("closed")
            read += n
        }
        return Response(status, headers, body)
    }
}
