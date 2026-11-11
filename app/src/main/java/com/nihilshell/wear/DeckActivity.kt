package com.nihilshell.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.nihilshell.wear.deck.Computer
import com.nihilshell.wear.deck.Deck
import com.nihilshell.wear.deck.DeckStore
import com.nihilshell.wear.ui.DeckView
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The computer's deck on the watch (docs/deck.md in Nihil-Shell): its state
 * every few seconds while it shows, as the phone asks for it, and a request
 * for each key and each step of the bezel or the crown. Out of sight it lets
 * go of its connection, and of the Wi-Fi it may have asked for.
 */
class DeckActivity : Activity() {
    private lateinit var view: DeckView
    private lateinit var computer: Computer
    private var deck: Deck? = null
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            refresh()
            main.postDelayed(this, POLL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DeckStore.get(this)
        val paired = store.computer
        val token = store.token()
        if (paired == null || token == null) {
            startActivity(Intent(this, ProbeActivity::class.java))
            finish()
            return
        }
        computer = paired
        deck = Deck(applicationContext, paired, token)
        view = DeckView(this).apply {
            name = paired.name
            onKey = { key(it) }
            onTurn = { turn(it) }
        }
        setContentView(view)
    }

    override fun onResume() {
        super.onResume()
        if (deck == null) return
        view.requestFocus()
        main.post(poll)
    }

    override fun onPause() {
        main.removeCallbacks(poll)
        deck?.let { worker.execute { it.close() } }
        super.onPause()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun refresh() = request("GET", "/v1/state") { view.state(it) }

    private fun key(key: String) {
        when (key) {
            "toggle", "next", "previous" -> request("POST", "/v1/media", JSONObject().put("action", key)) { main.postDelayed({ refresh() }, 400) }
            "caffeine", "night" -> request("POST", "/v1/$key", JSONObject().put("action", "toggle")) { view.toggle(key, it?.optBoolean("on") == true) }
            "lock" -> request("POST", "/v1/lock", JSONObject()) { view.say(getString(R.string.deck_locked, computer.name), false) }
        }
    }

    private fun turn(step: Int) = request("POST", "/v1/volume", JSONObject().put("action", if (step > 0) "up" else "down")) { view.volume(it) }

    /** One request, off the main thread; its answer back on it. */
    private fun request(method: String, path: String, body: JSONObject? = null, done: (JSONObject?) -> Unit) {
        val deck = deck ?: return
        worker.execute {
            val outcome = runCatching { deck.call(method, path, body) }
            main.post {
                if (isDestroyed) return@post
                val reply = outcome.getOrNull()
                when {
                    reply == null -> {
                        view.reached(false)
                        view.say(getString(R.string.deck_unreachable, computer.name), true)
                    }
                    reply.status == 401 -> forgotten()
                    reply.status in 200..299 -> {
                        view.reached(true)
                        done(reply.json)
                    }
                    else -> {
                        view.reached(true)
                        view.say(getString(R.string.deck_failed, computer.name), true)
                    }
                }
            }
        }
    }

    /** The computer forgot this watch: so does the watch. */
    private fun forgotten() {
        main.removeCallbacks(poll)
        DeckStore.get(this).forget()
        view.reached(false)
        view.say(getString(R.string.deck_forgotten, computer.name), true)
        main.postDelayed({ finish() }, 2600)
    }

    companion object {
        private const val POLL = 2500L
    }
}
