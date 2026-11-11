package com.nihilshell.wear.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nihilshell.wear.R
import com.nihilshell.wear.core.Mdns
import com.nihilshell.wear.core.Report
import com.nihilshell.wear.core.Way
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The network test on the watch, in the desktop's language: LEDs round the
 * screen's own edge, with light running round them while it measures; each
 * way to the computer a row with its LED (ink when it answered, red when it
 * failed, off before it is measured) and its milliseconds in dots. The bezel
 * or the crown turns it, as a drag does; a tap measures again.
 */
class ProbeView(context: Context) : View(context) {
    var onTap: () -> Unit = {}
    var onRotary: () -> Unit = {}

    var running = false
        set(value) {
            if (value && !field) started = SystemClock.uptimeMillis()
            field = value
            invalidate()
        }

    var rotary = 0
        set(value) {
            field = value
            invalidate()
        }

    private var report: Report? = null
    private val fonts = Nihil.fonts(context)
    private val round = resources.configuration.isScreenRound
    private val locale: Locale = resources.configuration.locales[0]
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val factor = ViewConfiguration.get(context).scaledVerticalScrollFactor
    private var leds = FloatArray(0)
    private var started = 0L
    private var offset = 0f
    private var bottom = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Nihil.VOID)
    }

    fun show(report: Report) {
        this.report = report
        contentDescription = describe(report)
        invalidate()
    }

    // --- The edge ----------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = min(w, h).toFloat()
        val inset = size * EDGE_INSET
        leds = FloatArray(LEDS * 2)
        if (round) {
            // From the top, clockwise, as the desktop's light climbs to meet there.
            val radius = size / 2f - inset
            for (i in 0 until LEDS) {
                val angle = -Math.PI / 2 + 2 * Math.PI * i / LEDS
                leds[2 * i] = (w / 2f + radius * cos(angle)).toFloat()
                leds[2 * i + 1] = (h / 2f + radius * sin(angle)).toFloat()
            }
        } else {
            val path = Path().apply { addRoundRect(RectF(inset, inset, w - inset, h - inset), size * CORNER, size * CORNER, Path.Direction.CW) }
            val measure = PathMeasure(path, true)
            val point = FloatArray(2)
            for (i in 0 until LEDS) {
                measure.getPosTan(measure.length * i / LEDS, point, null)
                leds[2 * i] = point[0]
                leds[2 * i + 1] = point[1]
            }
        }
    }

    /** Every LED is there, the unlit ones faintly; while it measures, a run of light goes round them. */
    private fun edge(canvas: Canvas, size: Float) {
        val radius = size * LED_RADIUS
        val head = if (running) (((SystemClock.uptimeMillis() - started) % LAP) * LEDS / LAP).toInt() else -1
        for (i in 0 until LEDS) {
            val behind = if (head < 0) RUN else (head - i + LEDS) % LEDS
            if (behind < RUN) {
                dots.color = Nihil.INK
                dots.alpha = 255 * (RUN - behind) / RUN
            } else {
                dots.color = Nihil.OFF
            }
            canvas.drawCircle(leds[2 * i], leds[2 * i + 1], radius, dots)
        }
    }

    // --- The content -------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (leds.isNotEmpty()) edge(canvas, size)
        canvas.save()
        canvas.translate(0f, -offset)
        bottom = content(canvas, size, width / 2f, height / 2f - size * TOP)
        canvas.restore()
        if (running) postInvalidateOnAnimation()
    }

    /** Everything under the edge, from [top] down: where it ends. */
    private fun content(canvas: Canvas, size: Float, cx: Float, top: Float): Float {
        val now = report
        var y = line(canvas, context.getString(R.string.probe_title), fonts.sans, size * 0.056f, Nihil.DIM, size, cx, top)
        val message = message(now)
        if (message != null) {
            y = say(canvas, message.first, message.second, size, cx, y + size * 0.03f)
            return footer(canvas, size, cx, y + size * 0.02f)
        }
        y = line(canvas, now?.computer.orEmpty(), fonts.sansMedium, size * 0.068f, Nihil.INK, size, cx, y + size * 0.01f)
        y += size * 0.02f
        y = way(canvas, size, cx, y, now?.default, network(now?.default?.network))
        y = way(canvas, size, cx, y, now?.wifi, context.getString(R.string.net_wifi))
        y = mdns(canvas, size, cx, y, now?.mdns)
        y = turns(canvas, size, cx, y)
        return footer(canvas, size, cx, y + size * 0.02f)
    }

    private fun way(canvas: Canvas, size: Float, cx: Float, y: Float, way: Way?, label: String): Float {
        if (way == null) return row(canvas, size, cx, y, Nihil.OFF, label, null, false, null, false)
        val cold = way.cold ?: return row(canvas, size, cx, y, Nihil.SIGNAL, label, null, false, error(way.error), true)
        if (way.error != null) return row(canvas, size, cx, y, Nihil.SIGNAL, label, cold, true, error(way.error), true)
        val warm = way.warm ?: cold
        val waited = way.waited
        val detail = if (waited != null) context.getString(R.string.detail_wifi, seconds(waited), warm) else context.getString(R.string.detail_warm, warm)
        return row(canvas, size, cx, y, Nihil.INK, label, cold, true, detail, false)
    }

    private fun mdns(canvas: Canvas, size: Float, cx: Float, y: Float, mdns: Mdns?): Float {
        val label = context.getString(R.string.mdns)
        if (mdns == null) return row(canvas, size, cx, y, Nihil.OFF, label, null, false, null, false)
        val found = mdns.found ?: return row(canvas, size, cx, y, Nihil.SIGNAL, label, null, false, error(mdns.error), true)
        return row(canvas, size, cx, y, Nihil.INK, label, found, true, mdns.address, false)
    }

    /** Whether the bezel or the crown reaches this app: the turns it has had while open. */
    private fun turns(canvas: Canvas, size: Float, cx: Float, y: Float): Float {
        val label = context.getString(R.string.rotary)
        return if (rotary == 0) {
            row(canvas, size, cx, y, Nihil.OFF, label, 0, false, context.getString(R.string.rotary_hint), false)
        } else {
            row(canvas, size, cx, y, Nihil.INK, label, rotary.toLong(), false, context.getString(R.string.rotary_ok), false)
        }
    }

    /**
     * One row: its LED, its name, its value in dots with its unit after it, and
     * a line under it, the rest of the numbers or, in ink, what went wrong.
     */
    private fun row(canvas: Canvas, size: Float, cx: Float, y: Float, led: Int, label: String, value: Long?, unit: Boolean, detail: String?, failed: Boolean): Float {
        val left = cx - size * WIDTH / 2f
        val right = cx + size * WIDTH / 2f
        val indent = left + size * 0.045f
        val baseline = y + size * 0.085f
        dots.color = led
        canvas.drawCircle(left + size * LED, baseline - size * 0.02f, size * LED, dots)

        var x = right
        if (value == null) {
            pen(fonts.sans, size * 0.056f, Nihil.MUTED)
            x -= type.measureText(DASH)
            canvas.drawText(DASH, x, baseline, type)
        } else {
            if (unit) {
                val ms = context.getString(R.string.unit_ms)
                pen(fonts.mono, size * 0.034f, Nihil.MUTED)
                x -= type.measureText(ms)
                canvas.drawText(ms, x, baseline, type)
                x -= size * 0.014f
            }
            val digits = value.toString()
            pen(fonts.dot, size * 0.085f, Nihil.INK)
            x -= type.measureText(digits)
            canvas.drawText(digits, x, baseline, type)
        }

        pen(fonts.sans, size * 0.056f, Nihil.INK)
        val name = TextUtils.ellipsize(label, type, max(0f, x - indent - size * 0.02f), TextUtils.TruncateAt.END)
        canvas.drawText(name, 0, name.length, indent, baseline, type)

        if (detail == null) return baseline + size * 0.035f
        pen(fonts.mono, size * 0.036f, if (failed) Nihil.INK else Nihil.MUTED)
        val shown = TextUtils.ellipsize(detail, type, right - indent, TextUtils.TruncateAt.END)
        canvas.drawText(shown, 0, shown.length, indent, baseline + size * 0.052f, type)
        return baseline + size * 0.075f
    }

    /** A line, centred and cut to fit: where it ends. */
    private fun line(canvas: Canvas, text: String, typeface: Typeface, textSize: Float, color: Int, size: Float, cx: Float, y: Float): Float {
        pen(typeface, textSize, color)
        val shown = TextUtils.ellipsize(text, type, size * WIDTH, TextUtils.TruncateAt.END)
        val baseline = y + textSize
        canvas.drawText(shown, 0, shown.length, cx - type.measureText(shown, 0, shown.length) / 2f, baseline, type)
        return baseline + textSize * 0.4f
    }

    /** A message in place of the rows; what went wrong in ink, with a red LED before it. */
    private fun say(canvas: Canvas, message: String, failed: Boolean, size: Float, cx: Float, y: Float): Float {
        var top = y
        if (failed) {
            dots.color = Nihil.SIGNAL
            canvas.drawCircle(cx, top + size * LED, size * LED, dots)
            top += size * 0.05f
        }
        pen(fonts.sans, size * 0.056f, if (failed) Nihil.INK else Nihil.DIM)
        val width = (size * WIDTH).toInt()
        val layout = StaticLayout.Builder.obtain(message, 0, message.length, type, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()
        canvas.save()
        canvas.translate(cx - width / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    private fun footer(canvas: Canvas, size: Float, cx: Float, y: Float): Float =
        line(canvas, context.getString(if (running) R.string.probe_measuring else R.string.probe_again), fonts.mono, size * 0.036f, Nihil.MUTED, size, cx, y)

    private fun pen(typeface: Typeface, textSize: Float, color: Int) {
        type.typeface = typeface
        type.textSize = textSize
        type.color = color
    }

    // --- Words ---------------------------------------------------------------------------

    /** The message that takes the rows' place: not paired, pairing, or why pairing failed. */
    private fun message(now: Report?): Pair<String, Boolean>? {
        if (now == null) return null
        val name = now.computer.orEmpty()
        return when {
            now.state == "unpaired" -> context.getString(R.string.probe_unpaired) to false
            now.state == "failed" -> context.getString(
                when (now.pairing) {
                    "wrong" -> R.string.probe_pair_wrong
                    "closed", "expired" -> R.string.probe_pair_expired
                    "wait" -> R.string.probe_pair_wait
                    else -> R.string.probe_pair_unreachable
                },
                name,
            ) to true
            now.pairing == "pairing" -> context.getString(R.string.probe_pairing, name) to false
            else -> null
        }
    }

    private fun network(name: String?): String = context.getString(
        when (name) {
            null -> R.string.net_default
            "bluetooth" -> R.string.net_bluetooth
            "wifi" -> R.string.net_wifi
            "cellular" -> R.string.net_cellular
            "ethernet" -> R.string.net_ethernet
            "none" -> R.string.net_none
            else -> R.string.net_other
        },
    )

    private fun error(reason: String?): String = when (reason) {
        "timeout" -> context.getString(R.string.error_timeout)
        "refused" -> context.getString(R.string.error_refused)
        "unreachable" -> context.getString(R.string.error_unreachable)
        "pin" -> context.getString(R.string.error_pin)
        "unpaired" -> context.getString(R.string.error_unpaired)
        "wait" -> context.getString(R.string.error_wait)
        "unavailable" -> context.getString(R.string.error_unavailable)
        "network" -> context.getString(R.string.error_network)
        "not-found" -> context.getString(R.string.error_not_found)
        else -> context.getString(R.string.error_other, reason ?: "?")
    }

    private fun seconds(ms: Long): String = String.format(locale, "%.1f", ms / 1000.0)

    /** What a screen reader says: the same as the screen, in one breath. */
    private fun describe(now: Report): String {
        val parts = arrayListOf(context.getString(R.string.probe_title))
        message(now)?.let {
            parts += it.first
            return parts.joinToString(". ")
        }
        now.computer?.let { parts += it }
        val ms = context.getString(R.string.unit_ms)
        now.default?.let { parts += network(it.network) + ": " + (it.cold?.let { cold -> "$cold $ms" } ?: error(it.error)) }
        now.wifi?.let { parts += context.getString(R.string.net_wifi) + ": " + (it.cold?.let { cold -> "$cold $ms" } ?: error(it.error)) }
        now.mdns?.let { parts += context.getString(R.string.mdns) + ": " + (it.found?.let { found -> "$found $ms" } ?: error(it.error)) }
        return parts.joinToString(". ")
    }

    // --- Touch and turns -----------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > slop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    scroll(lastY - event.y)
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> if (!dragging) performClick()
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    /** The crown, a rotating bezel or a touch bezel: Wear OS gives them all as the same scroll. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            scroll(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * factor)
            onRotary()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun scroll(dy: Float) {
        val size = min(width, height).toFloat()
        val room = max(0f, bottom - (height / 2f + size * 0.42f))
        offset = (offset + dy).coerceIn(0f, room)
        invalidate()
    }

    companion object {
        private const val LEDS = 60
        private const val RUN = 7
        private const val LAP = 1200L
        private const val EDGE_INSET = 0.03f
        private const val LED_RADIUS = 0.0075f
        private const val CORNER = 0.12f
        private const val TOP = 0.37f
        private const val WIDTH = 0.68f
        private const val LED = 0.012f
        private const val DASH = "—"
    }
}
