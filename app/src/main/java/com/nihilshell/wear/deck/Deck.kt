package com.nihilshell.wear.deck

import android.content.Context
import android.net.ConnectivityManager
import com.nihilshell.wear.core.Ipv4
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress

/**
 * The computer as this watch's deck (docs/deck.md in Nihil-Shell): its
 * requests, one at a time, over the way that reaches it. The default network
 * first, on most watches the phone's Bluetooth; when that doesn't reach the
 * computer, Wi-Fi asked for, and held until [close]. One connection kept open
 * as the deck keeps them (it closes them after 15 s idle), and opened again
 * when it closes. Blocking: off the main thread, and closed there too.
 */
class Deck(context: Context, val computer: Computer, private val token: String) : Closeable {
    class Reply(val status: Int, val json: JSONObject?)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val store = DeckStore.get(context)
    private var link: Link? = null
    private var open: Link.Open? = null
    private var wifi: Wifi? = null

    @Synchronized
    fun call(method: String, path: String, body: JSONObject? = null): Reply {
        val reused = open != null
        try {
            return ask(method, path, body)
        } catch (e: Exception) {
            drop()
            // A connection the computer closed while it waited: once more, on a new one.
            if (!reused) throw e
        }
        return try {
            ask(method, path, body)
        } catch (e: Exception) {
            drop()
            throw e
        }
    }

    private fun ask(method: String, path: String, body: JSONObject?): Reply {
        val (way, connection) = connect()
        val response = way.ask(connection, method, path, token, body?.toString())
        if (response.headers["connection"].equals("close", ignoreCase = true)) drop()
        return Reply(response.status, runCatching { JSONObject(response.text) }.getOrNull())
    }

    /** The connection: the one open, or a new one over the way that answered before, the default network, or Wi-Fi. */
    private fun connect(): Pair<Link, Link.Open> {
        val known = link
        open?.let { current -> if (known != null) return known to current }
        if (known != null) reach(known)?.let { return known to it }
        connectivity.activeNetwork?.let { network ->
            val default = Link(network, computer.port, computer.pin)
            reach(default)?.let {
                link = default
                return default to it
            }
        }
        val asked = wifi ?: Wifi(connectivity).also { wifi = it }
        val network = asked.await() ?: throw IOException("unreachable")
        val overWifi = Link(network, computer.port, computer.pin)
        val opened = reach(overWifi) ?: throw IOException("unreachable")
        link = overWifi
        return overWifi to opened
    }

    /** A connection over [way] to the address that answered last, or to the next that does. */
    private fun reach(way: Link): Link.Open? {
        for (address in addresses()) {
            val opened = runCatching { way.open(address) }.getOrNull() ?: continue
            open = opened
            if (computer.last != opened.host) {
                computer.last = opened.host
                store.save(computer)
            }
            return opened
        }
        return null
    }

    private fun addresses(): List<InetAddress> =
        (listOfNotNull(computer.last) + computer.hosts).distinct().mapNotNull { text -> Ipv4.parse(text)?.let { InetAddress.getByAddress(it) } }

    private fun drop() {
        open?.close()
        open = null
    }

    /** The connection goes, and the Wi-Fi asked for with it; the next request finds its way again. */
    @Synchronized
    override fun close() {
        drop()
        link = null
        wifi?.close()
        wifi = null
    }
}
