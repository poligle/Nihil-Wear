package com.nihilshell.wear.core

import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * What the computer's pairing QR holds (docs/deck.md in Nihil-Shell, Pairing),
 * handed to the watch by adb instead of a camera:
 * nihil-deck://pair?v=1&name=…&hosts=…&port=…&code=…&pin=…, and ble=…&psm=…
 * when the computer is on Bluetooth (its UUID and its LE channel).
 * Pure Kotlin, tested on the JVM.
 */
class Invitation(val name: String, val hosts: List<String>, val port: Int, val code: String, val pin: String, val psm: Int? = null) {
    companion object {
        private const val PREFIX = "nihil-deck://pair?"
        private val CODE = Regex("[A-HJ-NP-Z2-9]{8}")
        private val PIN = Regex("[A-Za-z0-9_-]{43}")

        /** The invitation in [text], or null when it isn't one a NIHIL computer gives. */
        fun parse(text: String): Invitation? {
            val trimmed = text.trim()
            if (!trimmed.startsWith(PREFIX)) return null
            val params = HashMap<String, String>()
            for (part in trimmed.substring(PREFIX.length).substringBefore('#').split('&')) {
                if (part.isEmpty()) continue
                val key = decode(part.substringBefore('=')) ?: return null
                val value = decode(part.substringAfter('=', "")) ?: return null
                params.putIfAbsent(key, value)
            }
            if (params["v"] != "1") return null
            val hosts = params["hosts"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val port = params["port"]?.toIntOrNull() ?: return null
            val code = params["code"].orEmpty()
            val pin = params["pin"].orEmpty()
            if (hosts.isEmpty() || hosts.any { Ipv4.parse(it) == null }) return null
            if (port !in 1..65535 || !CODE.matches(code) || !PIN.matches(pin)) return null
            // Over Bluetooth only when the UUID it announces is the one this pin makes.
            val psm = params["psm"]?.toIntOrNull()?.takeIf { it in 0x80..0xFF && params["ble"] == Ble.uuid(pin).toString() }
            return Invitation(params["name"].orEmpty().ifEmpty { hosts.first() }, hosts, port, code, pin, psm)
        }

        // As the deck writes it: Python's urlencode, a space as "+".
        private fun decode(text: String): String? = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrNull()
    }
}

/** The computer's addresses are IPv4 (docs/deck.md, Pairing): read without any lookup. */
object Ipv4 {
    fun parse(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
            val value = part.toInt()
            if (value > 255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }
}

/**
 * The UUID the computer announces itself by over Bluetooth (docs/deck.md in
 * Nihil-Shell, Without a network): the first 16 bytes of SHA-256 over
 * "nihil-deck ble", a zero byte and the pin, with the version (8) and the
 * variant set as RFC 9562 has them.
 */
object Ble {
    fun uuid(pin: String): UUID {
        val hash = MessageDigest.getInstance("SHA-256").digest("nihil-deck ble\u0000$pin".toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x80).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        val bytes = ByteBuffer.wrap(hash, 0, 16)
        return UUID(bytes.long, bytes.long)
    }
}
