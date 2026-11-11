package com.nihilshell.wear.deck

import android.net.Network
import android.util.Base64
import com.nihilshell.wear.core.Http
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * The way to the computer over one network, the default one or Wi-Fi asked
 * for: TLS that trusts only the certificate whose public key is the pin,
 * checked before anything is sent (docs/deck.md in Nihil-Shell, TLS and the
 * pin), and each step timed. Blocking: off the main thread.
 */
class Link(private val network: Network, private val port: Int, private val pin: String) {

    /** A connection, and how long its TCP and its TLS took, in ms. */
    class Open(val socket: SSLSocket, val host: String, val connect: Long, val handshake: Long) : Closeable {
        override fun close() {
            runCatching { socket.close() }
        }
    }

    /** The computer's answer, when it isn't 200: its status and its word for it. */
    class Refused(val status: Int) : IOException("HTTP $status") {
        val reason: String
            get() = when (status) {
                401 -> "unpaired"
                429 -> "wait"
                else -> "http-$status"
            }
    }

    private val tls: SSLContext by lazy { SSLContext.getInstance("TLS").apply { init(null, arrayOf(Pinned(pin)), null) } }

    fun open(address: InetAddress): Open {
        val plain = Socket()
        try {
            network.bindSocket(plain)
            val start = System.nanoTime()
            plain.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT)
            plain.tcpNoDelay = true
            val connected = System.nanoTime()
            val host = address.hostAddress ?: throw IOException("no address")
            val socket = tls.socketFactory.createSocket(plain, host, port, true) as SSLSocket
            socket.soTimeout = READ_TIMEOUT
            socket.startHandshake()
            return Open(socket, host, millis(start, connected), millis(connected, System.nanoTime()))
        } catch (e: Exception) {
            runCatching { plain.close() }
            throw e
        }
    }

    fun ask(open: Open, method: String, path: String, token: String? = null, body: String? = null): Http.Response {
        open.socket.outputStream.apply {
            write(Http.request(method, path, open.host, port, token, body))
            flush()
        }
        return Http.read(open.socket.inputStream)
    }

    /** Trusts one public key: the pin's (SHA-256 of its SubjectPublicKeyInfo, base64url). */
    private class Pinned(private val pin: String) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val key = chain.firstOrNull()?.publicKey?.encoded ?: throw CertificateException("none")
            val seen = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(key), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            if (!MessageDigest.isEqual(seen.toByteArray(), pin.toByteArray())) throw CertificateException("not this computer")
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw CertificateException("no clients")

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    companion object {
        const val CONNECT_TIMEOUT = 2500
        const val READ_TIMEOUT = 9000

        fun millis(from: Long, to: Long): Long = (to - from + 500_000) / 1_000_000

        /**
         * Why a way stopped, in the report's words: pin (not this computer),
         * timeout, unreachable, refused, network (it went away), what the
         * computer answered, or other.
         */
        fun reason(error: Throwable): String {
            var cause: Throwable? = error
            while (cause != null) {
                if (cause is CertificateException) return "pin"
                cause = cause.cause
            }
            val text = error.message.orEmpty()
            return when {
                error is Refused -> error.reason
                error is SocketTimeoutException -> "timeout"
                error is NoRouteToHostException || "ENETUNREACH" in text || "EHOSTUNREACH" in text -> "unreachable"
                "ECONNREFUSED" in text || error is ConnectException -> "refused"
                "EPERM" in text || "ENONET" in text || "Binding socket" in text -> "network"
                else -> "other"
            }
        }
    }
}
