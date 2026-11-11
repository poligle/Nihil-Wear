package com.nihilshell.wear.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.nihilshell.wear.ui.HaloView

/**
 * The light on the edge over any screen, the watch face too, as the phone's
 * over any app: a window of this accessibility service's, while the light
 * runs, which needs no other permission. It reads nothing on the screen and
 * only listens to NIHIL's own windows. The battery is its own business: red,
 * twice, at 15, 10 and 5 % going down and not charging, as the desktop warns;
 * the charger, two lights climbing to meet at the top.
 */
class HaloService : AccessibilityService(), Glyph.Listener {
    private val main = Handler(Looper.getMainLooper())
    private var view: HaloView? = null
    private var level = -1
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPulse(Glyph.Pulse.CHARGE)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val now = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / scale
                    val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    if (level > 0 && !charging && LOW.any { now <= it && level > it }) onPulse(Glyph.Pulse.SIGNAL)
                    level = now
                }
            }
        }
    }

    override fun onServiceConnected() {
        Glyph.listen(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(battery, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPulse(pulse: Glyph.Pulse) {
        main.post {
            val ring = view ?: add() ?: return@post
            ring.play(pulse) { remove() }
        }
    }

    private fun add(): HaloView? {
        val manager = getSystemService(WindowManager::class.java) ?: return null
        val ring = HaloView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching { manager.addView(ring, params) }.map { ring.also { view = it } }.getOrNull()
    }

    private fun remove() {
        val ring = view ?: return
        view = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(ring) }
    }

    private fun stop() {
        Glyph.forget(this)
        runCatching { unregisterReceiver(battery) }
        main.post { remove() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    companion object {
        private val LOW = intArrayOf(15, 10, 5)
    }
}
