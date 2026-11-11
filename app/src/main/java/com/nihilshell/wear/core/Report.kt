package com.nihilshell.wear.core

/**
 * One round on one way to the computer: a new connection (TCP, then TLS),
 * the first request on it, and a second on the same connection. Milliseconds.
 */
data class Round(val connect: Long, val handshake: Long, val hello: Long, val again: Long) {
    /** What a first tap costs: a new connection and its first answer. */
    val cold: Long get() = connect + handshake + hello
}

/**
 * One way to the computer: the network it went over, how long that network
 * took to come up when it was asked for, the address that answered, its
 * rounds, a request with the token on top ([state], the computer's own work
 * included), and why it stopped, if it did.
 */
data class Way(
    val network: String,
    val waited: Long? = null,
    val address: String? = null,
    val rounds: List<Round> = emptyList(),
    val state: Long? = null,
    val error: String? = null,
) {
    val cold: Long? get() = median(rounds.map { it.cold })
    val warm: Long? get() = median(rounds.map { it.again })

    fun json(): String = buildString {
        append("{\"network\":").append(Json.quote(network))
        append(",\"waited\":").append(Json.number(waited))
        append(",\"address\":").append(Json.quote(address))
        append(",\"cold\":").append(Json.number(cold))
        append(",\"warm\":").append(Json.number(warm))
        append(",\"state\":").append(Json.number(state))
        append(",\"error\":").append(Json.quote(error))
        append(",\"rounds\":[")
        rounds.forEachIndexed { i, round ->
            if (i > 0) append(',')
            append("{\"connect\":").append(round.connect)
            append(",\"handshake\":").append(round.handshake)
            append(",\"hello\":").append(round.hello)
            append(",\"again\":").append(round.again).append('}')
        }
        append("]}")
    }
}

/** The computer as mDNS finds it (_nihil-deck._tcp): on which networks, in how long, at what address. */
data class Mdns(val network: String, val found: Long? = null, val address: String? = null, val error: String? = null) {
    fun json(): String =
        "{\"network\":${Json.quote(network)},\"found\":${Json.number(found)},\"address\":${Json.quote(address)},\"error\":${Json.quote(error)}}"
}

/**
 * A test, as the watch shows it and tools/watch prints it. [state] is idle,
 * running, done, unpaired or failed (the pairing, with [pairing] saying why);
 * [run] counts the tests, so a fresh report can be told from the last one.
 */
data class Report(
    val run: Int,
    val state: String,
    val at: Long,
    val device: String,
    val sdk: Int,
    val computer: String? = null,
    val pairing: String? = null,
    val default: Way? = null,
    val wifi: Way? = null,
    val mdns: Mdns? = null,
    val rotary: Int = 0,
) {
    fun json(): String = buildString {
        append("{\"run\":").append(run)
        append(",\"state\":").append(Json.quote(state))
        append(",\"at\":").append(at)
        append(",\"device\":").append(Json.quote(device))
        append(",\"sdk\":").append(sdk)
        append(",\"computer\":").append(Json.quote(computer))
        append(",\"pairing\":").append(Json.quote(pairing))
        append(",\"default\":").append(default?.json() ?: "null")
        append(",\"wifi\":").append(wifi?.json() ?: "null")
        append(",\"mdns\":").append(mdns?.json() ?: "null")
        append(",\"rotary\":").append(rotary).append('}')
    }
}

/** The middle value; with an even count, halfway between the two in the middle. */
fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** Just enough JSON to write a report: the app has no library for it, and org.json can't run in the JVM tests. */
object Json {
    fun quote(text: String?): String {
        if (text == null) return "null"
        val out = StringBuilder(text.length + 2).append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        return out.append('"').toString()
    }

    fun number(value: Long?): String = value?.toString() ?: "null"
}
