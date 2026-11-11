package com.nihilshell.wear.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.nihilshell.wear.system.Glyph
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The light on the edge, drawn: a run of LEDs on the face's sixty, round the
 * screen's own shape, and only the lit ones, over whatever is under it. Once
 * round in ink for a notification; twice in red for a call, an alarm or a
 * low battery; from the bottom up both sides for the charger.
 */
class HaloView(context: Context) : View(context) {
    private val round = resources.configuration.isScreenRound
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private var leds = FloatArray(0)
    private var pulse: Glyph.Pulse? = null
    private var start = 0L
    private var done: (() -> Unit)? = null

    fun play(pulse: Glyph.Pulse, done: () -> Unit) {
        this.pulse = pulse
        this.done = done
        start = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h).toFloat()
        val inset = size * 0.03f
        leds = FloatArray(LEDS * 2)
        if (round) {
            val radius = size / 2f - inset
            for (i in 0 until LEDS) {
                val angle = -Math.PI / 2 + 2 * Math.PI * i / LEDS
                leds[2 * i] = (w / 2f + radius * cos(angle)).toFloat()
                leds[2 * i + 1] = (h / 2f + radius * sin(angle)).toFloat()
            }
        } else {
            val path = Path().apply { addRoundRect(RectF(inset, inset, w - inset, h - inset), size * 0.12f, size * 0.12f, Path.Direction.CW) }
            val measure = PathMeasure(path, true)
            val point = FloatArray(2)
            for (i in 0 until LEDS) {
                measure.getPosTan(measure.length * i / LEDS, point, null)
                leds[2 * i] = point[0]
                leds[2 * i + 1] = point[1]
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = pulse ?: return
        if (leds.isEmpty()) {
            postInvalidateOnAnimation()
            return
        }
        val elapsed = SystemClock.uptimeMillis() - start
        val radius = min(width, height) * 0.0085f
        when (now) {
            Glyph.Pulse.CHARGE -> {
                val t = elapsed.toFloat() / RISE
                if (t >= 1f) return finish()
                // From the bottom, up both sides, to meet at the top.
                val head = (t * LEDS / 2).toInt()
                for (k in 0 until RUN) {
                    val climbed = head - k
                    if (climbed < 0) continue
                    val alpha = envelope(t) * (RUN - k) / RUN
                    led(canvas, LEDS / 2 + climbed, Nihil.INK, alpha, radius)
                    led(canvas, LEDS / 2 - climbed, Nihil.INK, alpha, radius)
                }
            }
            else -> {
                val laps = if (now == Glyph.Pulse.SIGNAL) 2 else 1
                val t = elapsed.toFloat() / SWEEP
                if (t >= laps) return finish()
                val lap = t - t.toInt()
                val head = (lap * LEDS).toInt()
                val colour = if (now == Glyph.Pulse.SIGNAL) Nihil.SIGNAL else Nihil.INK
                for (k in 0 until RUN) led(canvas, head - k, colour, envelope(lap) * (RUN - k) / RUN, radius)
            }
        }
        postInvalidateOnAnimation()
    }

    /** In and out as the desktop's light: up over the first eighth, down over the last fifth. */
    private fun envelope(t: Float): Float = min(1f, t / 0.12f) * min(1f, (1f - t) / 0.18f).coerceAtLeast(0f)

    private fun led(canvas: Canvas, index: Int, colour: Int, alpha: Float, radius: Float) {
        val i = ((index % LEDS) + LEDS) % LEDS
        dots.color = colour
        dots.alpha = (255 * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawCircle(leds[2 * i], leds[2 * i + 1], radius, dots)
    }

    private fun finish() {
        pulse = null
        invalidate()
        done?.invoke()
        done = null
    }

    companion object {
        private const val LEDS = 60
        private const val RUN = 8
        private const val SWEEP = 1100L
        private const val RISE = 1500L
    }
}
