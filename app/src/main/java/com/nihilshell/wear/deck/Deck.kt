package com.nihilshell.wear.deck

import android.content.Context
import android.net.ConnectivityManager
import com.nihilshell.wear.core.Http
import com.nihilshell.wear.core.Ipv4
import com.nihilshell.wear.core.TlsPipe
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress

/**
 * The computer as this watch's deck (docs/deck.md in Nihil-Shell): its
 * requests, one at a time, over the way that reaches it. The default network
 * first, on most watches the phone's Bluetooth; when that doesn't reach the
 * computer, Bluetooth LE straight to it, if it is on it and near; then Wi-Fi
 * asked for, and held until [close]. One connection kept open as the deck
 * keeps them (it closes them after 15 s idle), and opened again when it
 * closes. Blocking: off the main thread, and closed there too.
 */
class Deck(private val context: Context, val computer: Computer, private val token: String) : Closeable {
    class Reply(val status: Int, val json: JSONObject?)

    /** A connection open to the computer, over a network or Bluetooth. */
    private interface Line : Closeable {
        fun ask(method: String, path: String, token: String, body: String?): Http.Response
    }

    private class Net(private val way: Link, private val open: Link.Open) : Line {
        override fun ask(method: String, path: String, token: String, body: String?) = way.ask(open, method, path, token, body)

        override fun close() = open.close()
    }

    private class Air(private val pipe: TlsPipe, private val port: Int) : Line {
        override fun ask(method: String, path: String, token: String, body: String?): Http.Response {
            pipe.output.write(Http.request(method, path, Bluetooth.HOST, port, token, body))
            pipe.output.flush()
            return Http.read(pipe.input)
        }

        override fun close() = pipe.close()
    }

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val store = DeckStore.get(context)
    private var link: Link? = null
    private var line: Line? = null
    private var wifi: Wifi? = null
    private var overBluetooth = false

    @Synchronized
    fun call(method: String, path: String, body: JSONObject? = null): Reply {
        val reused = line != null
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
        val response = connect().ask(method, path, token, body?.toString())
        if (response.headers["connection"].equals("close", ignoreCase = true)) drop()
        return Reply(response.status, runCatching { JSONObject(response.text) }.getOrNull())
    }

    /**
     * The connection: the one open, or a new one over the way that answered
     * before, the default network, Bluetooth, or Wi-Fi.
     */
    private fun connect(): Line {
        line?.let { return it }
        if (overBluetooth) air()?.let { return it }
        val known = link
        if (known != null) reach(known)?.let { return it }
        connectivity.activeNetwork?.let { network ->
            val default = Link(network, computer.port, computer.pin)
            reach(default)?.let {
                link = default
                return it
            }
        }
        if (!overBluetooth) {
            air()?.let {
                overBluetooth = true
                return it
            }
        }
        val asked = wifi ?: Wifi(connectivity).also { wifi = it }
        val network = asked.await() ?: throw IOException("unreachable")
        val overWifi = Link(network, computer.port, computer.pin)
        val opened = reach(overWifi) ?: throw IOException("unreachable")
        link = overWifi
        return opened
    }

    /** A connection over [way] to the address that answered last, or to the next that does. */
    private fun reach(way: Link): Line? {
        for (address in addresses()) {
            val opened = runCatching { way.open(address) }.getOrNull() ?: continue
            if (computer.last != opened.host) {
                computer.last = opened.host
                store.save(computer)
            }
            overBluetooth = false
            return Net(way, opened).also { line = it }
        }
        return null
    }

    /** A channel straight to the computer, when it said it is on Bluetooth; let go when nothing goes for a while. */
    private fun air(): Line? {
        val psm = computer.psm ?: return null
        val pipe = Bluetooth.open(context, computer.pin, psm) ?: return null
        pipe.idle(Link.READ_TIMEOUT.toLong())
        return Air(pipe, computer.port).also { line = it }
    }

    private fun addresses(): List<InetAddress> =
        (listOfNotNull(computer.last) + computer.hosts).distinct().mapNotNull { text -> Ipv4.parse(text)?.let { InetAddress.getByAddress(it) } }

    private fun drop() {
        line?.close()
        line = null
    }

    /** The connection goes, and the Wi-Fi asked for with it; the next request finds its way again. */
    @Synchronized
    override fun close() {
        drop()
        link = null
        overBluetooth = false
        wifi?.close()
        wifi = null
    }
}
