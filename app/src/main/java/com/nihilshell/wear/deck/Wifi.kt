package com.nihilshell.wear.deck

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Wi-Fi asked for, as Wear OS wants it for what the phone's Bluetooth can't
 * carry, and held until closed. How long it took to come up is part of the
 * network test's answer.
 */
class Wifi(private val connectivity: ConnectivityManager) : Closeable {
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
