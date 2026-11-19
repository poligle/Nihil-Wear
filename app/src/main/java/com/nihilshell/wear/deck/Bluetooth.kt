package com.nihilshell.wear.deck

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import com.nihilshell.wear.core.Ble
import com.nihilshell.wear.core.TlsPipe
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The computer over Bluetooth LE, straight to it, when no network reaches it
 * (docs/deck.md in Nihil-Shell, Without a network): found by the UUID its pin
 * makes, and an LE channel (L2CAP) to it on the PSM it gave, with nothing to
 * pair in the watch's settings. On the channel go the same TLS, the pin
 * checked, and the same HTTP as over a network. Blocking: off the main thread.
 */
object Bluetooth {
    val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    const val HOST = "nihil"

    private const val LOOK = 5000L          // to find the computer, which announces itself every second or so
    private const val CONNECT = 6000L       // for a channel to open
    private const val HANDSHAKE = 9000L
    private const val AGAIN = 20_000L       // after the computer wasn't found or didn't open, before trying again

    private class Seen(val uuid: UUID, val device: BluetoothDevice, val at: Long)

    @Volatile
    private var seen: Seen? = null

    @Volatile
    private var missed = 0L

    private val looking = Any()
    private val clock = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "nihil-bluetooth").apply { isDaemon = true } }

    fun allowed(context: Context): Boolean = PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** The PSM in what the computer says of its Bluetooth ({"uuid", "psm"}), if it is this pin's. */
    fun psm(ble: JSONObject?, pin: String): Int? {
        if (ble == null) return null
        return ble.optInt("psm", 0).takeIf { it in 0x80..0xFF && ble.optString("uuid") == Ble.uuid(pin).toString() }
    }

    /** A channel to the computer with TLS on it, the pin checked; null when Bluetooth is off, not allowed, or the computer isn't near. */
    @SuppressLint("MissingPermission")
    fun open(context: Context, pin: String, psm: Int): TlsPipe? {
        if (!allowed(context) || SystemClock.elapsedRealtime() - missed < AGAIN) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        val uuid = Ble.uuid(pin)
        val known = seen?.takeIf { it.uuid == uuid }?.device
        val socket = known?.let { channel(it, psm) } ?: find(adapter, uuid)?.let { channel(it, psm) }
        if (socket == null) {
            missed = SystemClock.elapsedRealtime()
            return null
        }
        val engine = Link.tls(pin).createSSLEngine(HOST, 443).apply { useClientMode = true }
        val pipe = TlsPipe(engine, socket.inputStream, socket.outputStream) { socket.close() }
        return try {
            pipe.idle(HANDSHAKE)
            pipe.handshake()
            pipe
        } catch (e: Exception) {
            pipe.close()
            missed = SystemClock.elapsedRealtime()
            null
        }
    }

    /** Looks for the computer's advertisement, one look at a time: Android allows an app only a few a minute. */
    @SuppressLint("MissingPermission")
    private fun find(adapter: BluetoothAdapter, uuid: UUID): BluetoothDevice? {
        synchronized(looking) {
            seen?.takeIf { it.uuid == uuid && SystemClock.elapsedRealtime() - it.at < LOOK }?.let { return it.device }
            val scanner = adapter.bluetoothLeScanner ?: return null
            val found = AtomicReference<BluetoothDevice?>()
            val done = CountDownLatch(1)
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    found.compareAndSet(null, result.device)
                    done.countDown()
                }

                override fun onScanFailed(errorCode: Int) = done.countDown()
            }
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            try {
                scanner.startScan(listOf(filter), settings, callback)
            } catch (e: RuntimeException) {
                return null
            }
            done.await(LOOK, TimeUnit.MILLISECONDS)
            runCatching { scanner.stopScan(callback) }
            val device = found.get() ?: return null
            seen = Seen(uuid, device, SystemClock.elapsedRealtime())
            return device
        }
    }

    /** An LE channel to the device; insecure, as Android calls one with no bonding: TLS is the check. */
    @SuppressLint("MissingPermission")
    private fun channel(device: BluetoothDevice, psm: Int): BluetoothSocket? {
        val socket = try {
            device.createInsecureL2capChannel(psm)
        } catch (e: Exception) {
            return null
        }
        val stop = clock.schedule({ runCatching { socket.close() } }, CONNECT, TimeUnit.MILLISECONDS)
        return try {
            socket.connect()
            socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            null
        } finally {
            stop.cancel(false)
        }
    }
}
