package com.nihilshell.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import com.nihilshell.wear.probe.Live
import com.nihilshell.wear.probe.Probe
import com.nihilshell.wear.ui.ProbeView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * NIHIL on the watch, for now: the network test. It measures as it opens,
 * when tapped, and when tools/watch opens it again, with the screen kept on
 * meanwhile: a test from the background would measure what Wear OS lets an
 * app in the background do, not what a tap does.
 */
class ProbeActivity : Activity() {
    private lateinit var view: ProbeView
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = ProbeView(this).apply {
            onTap = { measure() }
            onRotary = {
                Live.rotary++
                rotary = Live.rotary
            }
            rotary = Live.rotary
            Live.report?.let { show(it) }
        }
        setContentView(view)
        measure()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        measure()
    }

    override fun onResume() {
        super.onResume()
        view.requestFocus()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun measure() {
        if (running) return
        running = true
        view.running = true
        val context = applicationContext
        val run = Live.next(context)
        worker.execute {
            val report = Probe(context, run) { partial -> runOnUiThread { if (!isDestroyed) view.show(partial) } }.run()
            runOnUiThread {
                running = false
                if (!isDestroyed) {
                    view.running = false
                    view.show(report)
                }
            }
        }
    }
}
