package com.nihilshell.wear.core

/**
 * The bezel or the crown as steps: what it turns, gathered until it makes a
 * whole step either way. A crown gives fractions, a touch bezel whole clicks.
 * Pure Kotlin, tested on the JVM.
 */
class Turns(private val step: Float = 1f) {
    private var gathered = 0f

    /** The whole steps [amount] completes: positive up, negative down. */
    fun add(amount: Float): Int {
        gathered += amount
        val whole = (gathered / step).toInt()
        gathered -= whole * step
        return whole
    }

    fun reset() {
        gathered = 0f
    }
}
