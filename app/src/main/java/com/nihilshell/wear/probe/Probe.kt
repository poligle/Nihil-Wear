package com.nihilshell.wear.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.nihilshell.wear.core.Invitation
import com.nihilshell.wear.core.Ipv4
import com.nihilshell.wear.core.Mdns
import com.nihilshell.wear.core.Report
import com.nihilshell.wear.core.Round
import com.nihilshell.wear.core.Way
import com.nihilshell.wear.deck.Computer
import com.nihilshell.wear.deck.DeckStore
import com.nihilshell.wear.deck.Link
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * How this watch reaches the computer. It pairs first when adb left an
 * invitation (tools/watch invite); then the same rounds over the default
 * network, on a watch usually the phone's Bluetooth, and over Wi-Fi asked
 * for; and mDNS while that Wi-Fi is up. Blocking, a few seconds to half a
 * minute: off the main thread. [update] gets each step as it comes.
 */
class Probe(private val context: Context, run: Int, private val update: (Report) -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val store = DeckStore.get(context)
    private var report = Report(run, "running", System.currentTimeMillis(), deviceName(context), Build.VERSION.SDK_INT)

    fun run(): Report {
        publish()
        val text = store.invitation
        val invitation = text?.let { Invitation.parse(it) }
        if (text != null && invitation == null) store.invitation = null
        if (invitation != null && !pair(invitation)) return finish("failed")
        val computer = store.computer
        val token = store.token()
        if (computer == null || token == null) return finish("unpaired")
        report = report.copy(computer = computer.name)
        publish()

        val active = connectivity.activeNetwork
        report = report.copy(default = if (active == null) Way("none", error = "network") else measure(active, label(active), null, computer, token))
        publish()

        Wifi(connectivity).use { wifi ->
            val network = wifi.await()
            if (network == null) {
                report = report.copy(wifi = Way("wifi", error = "unavailable"))
                publish()
            } else {
                report = report.copy(wifi = measure(network, "wifi", wifi.waited, computer, token))
                publish()
            }
            report = report.copy(mdns = discover(network))
        }
        return finish("done")
    }

    // --- Pairing, as the phone does with the QR (docs/deck.md, Pairing) --------------

    private fun pair(invitation: Invitation): Boolean {
        report = report.copy(computer = invitation.name, pairing = "pairing")
        publish()
        val addresses = invitation.hosts.mapNotNull { address(it) }
        var outcome = connectivity.activeNetwork?.let { pairOver(it, invitation, addresses) } ?: UNREACHABLE
        if (outcome == UNREACHABLE) {
            outcome = Wifi(connectivity).use { wifi -> wifi.await()?.let { pairOver(it, invitation, addresses) } ?: UNREACHABLE }
        }
        // The code pairs once and for five minutes: only an unreachable computer, or one
        // that asks to wait, leaves it worth another try.
        if (outcome != UNREACHABLE && outcome != "wait") store.invitation = null
        report = report.copy(pairing = outcome)
        return outcome == "paired"
    }

    /** "paired", or why not: wrong, closed, expired, wait (docs/deck.md, Errors) or unreachable. */
    private fun pairOver(network: Network, invitation: Invitation, addresses: List<InetAddress>): String {
        val link = Link(network, invitation.port, invitation.pin)
        for (address in addresses) {
            val open = try {
                link.open(address)
            } catch (e: Exception) {
                continue
            }
            try {
                if (link.ask(open, "GET", "/v1/hello").status != 200) continue
                val body = JSONObject().put("code", invitation.code).put("device", deviceName(context)).toString()
                val reply = link.ask(open, "POST", "/v1/pair", body = body)
                val json = runCatching { JSONObject(reply.text) }.getOrNull()
                if (reply.status != 200 || json == null) return json?.optString("error")?.takeIf { it.isNotEmpty() } ?: UNREACHABLE
                val name = json.optString("name").ifEmpty { invitation.name }
                store.save(Computer(name, invitation.pin, invitation.port, invitation.hosts, open.host, json.optString("device")), json.getString("token"))
                return "paired"
            } catch (e: Exception) {
                continue
            } finally {
                open.close()
            }
        }
        return UNREACHABLE
    }

    // --- The rounds ------------------------------------------------------------------

    /** [ROUNDS] rounds over [network], at the address that answers first, and a request with the token on top. */
    private fun measure(network: Network, label: String, waited: Long?, computer: Computer, token: String): Way {
        val link = Link(network, computer.port, computer.pin)
        val rounds = ArrayList<Round>()
        var used: InetAddress? = null
        var failure: Throwable? = null
        val candidates = addresses(computer)
        while (rounds.size < ROUNDS) {
            var done: Round? = null
            for (address in used?.let { listOf(it) } ?: candidates) {
                try {
                    done = round(link, address)
                    used = address
                    break
                } catch (e: Exception) {
                    failure = e
                }
            }
            if (done == null) break
            rounds += done
        }
        val at = used ?: return Way(label, waited, error = Link.reason(failure ?: IOException("no address")))
        val host = at.hostAddress
        if (host != null && host != computer.last) {
            computer.last = host
            store.save(computer)
        }
        val cut = if (rounds.size < ROUNDS) failure?.let { Link.reason(it) } else null
        return try {
            Way(label, waited, host, rounds, authorized(link, at, token), cut)
        } catch (e: Exception) {
            Way(label, waited, host, rounds, error = Link.reason(e))
        }
    }

    /** A new connection, hello on it, and hello again on the same connection. */
    private fun round(link: Link, address: InetAddress): Round = link.open(address).use { open ->
        val start = System.nanoTime()
        val hello = link.ask(open, "GET", "/v1/hello")
        val first = System.nanoTime()
        if (hello.status != 200) throw Link.Refused(hello.status)
        val again = link.ask(open, "GET", "/v1/hello")
        val second = System.nanoTime()
        if (again.status != 200) throw Link.Refused(again.status)
        Round(open.connect, open.handshake, Link.millis(start, first), Link.millis(first, second))
    }

    /** What the deck asks most: the state, with the token, the computer's own work included. */
    private fun authorized(link: Link, address: InetAddress, token: String): Long = link.open(address).use { open ->
        val start = System.nanoTime()
        val reply = link.ask(open, "GET", "/v1/state", token)
        val end = System.nanoTime()
        if (reply.status != 200) throw Link.Refused(reply.status)
        Link.millis(start, end)
    }

    // --- mDNS (docs/deck.md, Finding the computer again) -----------------------------

    /** The first _nihil-deck._tcp that resolves, on [network], or on every network when there is none. */
    private fun discover(network: Network?): Mdns {
        val where = if (network == null) "all" else "wifi"
        val nsd = context.getSystemService(NsdManager::class.java) ?: return Mdns(where, error = "unavailable")
        val start = System.nanoTime()
        val found = AtomicReference<Pair<Long, String>?>()
        val resolving = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!resolving.compareAndSet(false, true)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = resolving.set(false)

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = info.host?.hostAddress
                        if (host == null) {
                            resolving.set(false)
                            return
                        }
                        found.compareAndSet(null, Link.millis(start, System.nanoTime()) to host)
                        done.countDown()
                    }
                })
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = done.countDown()

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceLost(info: NsdServiceInfo) = Unit
        }
        val started = runCatching { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, network, context.mainExecutor, listener) }.isSuccess
        if (!started) return Mdns(where, error = "unavailable")
        done.await(MDNS_TIMEOUT, TimeUnit.MILLISECONDS)
        runCatching { nsd.stopServiceDiscovery(listener) }
        val hit = found.get() ?: return Mdns(where, error = "not-found")
        return Mdns(where, hit.first, hit.second)
    }

    // --- The rest --------------------------------------------------------------------

    private fun addresses(computer: Computer): List<InetAddress> =
        (listOfNotNull(computer.last) + computer.hosts).distinct().mapNotNull { address(it) }

    private fun address(text: String): InetAddress? = Ipv4.parse(text)?.let { InetAddress.getByAddress(it) }

    private fun label(network: Network): String {
        val caps = connectivity.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun publish() {
        val now = report
        Live.report = now
        update(now)
    }

    private fun finish(state: String): Report {
        report = report.copy(state = state, at = System.currentTimeMillis())
        publish()
        Live.save(context, report)
        return report
    }

    /**
     * Wi-Fi asked for, as Wear OS wants it for what Bluetooth can't do, and
     * held until closed: how long it took to come up is part of the answer.
     */
    private class Wifi(private val connectivity: ConnectivityManager) : Closeable {
        private val start = System.nanoTime()
        private val up = AtomicLong(0L)
        private val network = AtomicReference<Network?>()
        private val done = CountDownLatch(1)
        private val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (this@Wifi.network.compareAndSet(null, network)) up.set(System.nanoTime())
                done.countDown()
            }

            override fun onUnavailable() = done.countDown()
        }
        private val asked = runCatching {
            connectivity.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback, TIMEOUT)
        }.isSuccess

        /** Milliseconds until Wi-Fi was up, or null when it didn't come. */
        val waited: Long?
            get() = up.get().takeIf { it != 0L }?.let { Link.millis(start, it) }

        /** Wi-Fi, once it is up, or null when it didn't come up in time. */
        fun await(): Network? {
            if (!asked) return null
            done.await(TIMEOUT + 1000L, TimeUnit.MILLISECONDS)
            return network.get()
        }

        override fun close() {
            if (asked) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }

        companion object {
            const val TIMEOUT = 15_000
        }
    }

    companion object {
        const val ROUNDS = 3
        const val MDNS_TIMEOUT = 6000L
        const val SERVICE = "_nihil-deck._tcp"
        private const val UNREACHABLE = "unreachable"
    }
}

/** How the watch calls itself, as the phone does when it pairs: its name in Settings, or its model. */
fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
