package com.nihilshell.wear.system

import java.util.concurrent.CopyOnWriteArraySet

/**
 * The light on the edge, as the desktop's and the phone's: who asks for it
 * (GlyphListener, HaloService's battery) and who draws it (HaloService).
 *
 *   NOTIFY  once round the edge, in ink: a notification
 *   SIGNAL  the same in red, twice: a call, an alarm, a battery running low
 *   CHARGE  two lights climbing both sides from the bottom to meet at the top
 */
object Glyph {
    enum class Pulse { NOTIFY, SIGNAL, CHARGE }

    fun interface Listener {
        fun onPulse(pulse: Pulse)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    fun listen(listener: Listener) {
        listeners += listener
    }

    fun forget(listener: Listener) {
        listeners -= listener
    }

    fun pulse(pulse: Pulse) = listeners.forEach { it.onPulse(pulse) }
}
