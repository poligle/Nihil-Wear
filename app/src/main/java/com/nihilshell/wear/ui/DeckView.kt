package com.nihilshell.wear.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nihilshell.wear.R
import com.nihilshell.wear.core.Turns
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The computer's deck on the watch: what plays and its keys; the bezel or
 * the crown turns the volume, which the LEDs round the edge show, as the
 * desktop's light goes round its frame; the computer's caffeine, night light
 * and lock under it, caffeine and night light lit in ink while they are on. A
 * LED before the computer's name says whether it answers; what went wrong is
 * said in ink, with a red LED before it.
 */
class DeckView(context: Context) : View(context) {
    var name = ""
    var onKey: (String) -> Unit = {}
    var onTurn: (Int) -> Unit = {}

    private var title: String? = null
    private var artist: String? = null
    private var playing = false
    private var level: Int? = null
    private var muted = false
    private val on = HashMap<String, Boolean>()
    private var answered: Boolean? = null
    private var message: String? = null
    private var failed = false
    private var until = 0L
    private var pressed: String? = null

    private val fonts = Nihil.fonts(context)
    private val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Path()
    private val config = ViewConfiguration.get(context)
    private val turns = Turns(STEP)
    private val keys = LinkedHashMap<String, FloatArray>()
    private val icons: Map<String, Drawable?> = mapOf(
        "caffeine" to context.getDrawable(R.drawable.mdi_coffee),
        "night" to context.getDrawable(R.drawable.mdi_night),
        "lock" to context.getDrawable(R.drawable.mdi_lock),
    )
    private var downX = 0f
    private var downY = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Nihil.VOID)
    }

    /** GET /v1/state as the deck answers it (docs/deck.md). */
    fun state(json: JSONObject?) {
        val media = json?.optJSONObject("media")
        title = media?.optString("title")?.takeIf { it.isNotBlank() }
        artist = media?.optString("artist")?.takeIf { it.isNotBlank() }
        playing = media?.optBoolean("playing") == true
        volume(json?.optJSONObject("volume"))
        if (json != null) {
            on["caffeine"] = json.optBoolean("caffeine")
            on["night"] = json.optBoolean("night")
        }
        invalidate()
    }

    fun volume(json: JSONObject?) {
        level = json?.takeIf { it.has("level") }?.optInt("level")
        muted = json?.optBoolean("muted") == true
        invalidate()
    }

    fun toggle(key: String, lit: Boolean) {
        on[key] = lit
        invalidate()
    }

    fun reached(ok: Boolean) {
        answered = ok
        invalidate()
    }

    /** A sentence for a while in place of what plays; [failed] puts a red LED before it. */
    fun say(text: String, failed: Boolean) {
        message = text
        this.failed = failed
        until = SystemClock.uptimeMillis() + SAY
        invalidate()
        postInvalidateDelayed(SAY + 20)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        edge(canvas, size, cx, cy)

        // The computer, with whether it answers.
        type.typeface = fonts.sans
        type.textSize = size * 0.05f
        type.color = Nihil.DIM
        val label = TextUtils.ellipsize(name, type, size * 0.5f, TextUtils.TruncateAt.END)
        val labelWidth = type.measureText(label, 0, label.length)
        val left = cx - (labelWidth + size * 0.045f) / 2f
        dots.color = when (answered) {
            true -> Nihil.INK
            false -> Nihil.SIGNAL
            null -> Nihil.OFF
        }
        canvas.drawCircle(left + size * 0.012f, cy - size * 0.315f, size * 0.012f, dots)
        canvas.drawText(label, 0, label.length, left + size * 0.045f, cy - size * 0.3f, type)

        // What plays, or what there is to say.
        val saying = message?.takeIf { SystemClock.uptimeMillis() < until }
        if (saying != null) {
            if (failed) {
                dots.color = Nihil.SIGNAL
                canvas.drawCircle(cx, cy - size * 0.235f, size * 0.012f, dots)
            }
            centred(canvas, saying, fonts.sans, size * 0.056f, if (failed) Nihil.INK else Nihil.DIM, size * 0.66f, cx, cy - size * 0.16f)
        } else {
            centred(canvas, title ?: context.getString(R.string.deck_nothing), fonts.sansMedium, size * 0.064f, if (title != null) Nihil.INK else Nihil.MUTED, size * 0.66f, cx, cy - size * 0.165f)
            artist?.let { centred(canvas, it, fonts.mono, size * 0.04f, Nihil.MUTED, size * 0.6f, cx, cy - size * 0.1f) }
        }

        // The music's keys.
        keys.clear()
        key(canvas, "previous", cx - size * 0.2f, cy + size * 0.03f, size * 0.062f, size)
        key(canvas, "toggle", cx, cy + size * 0.03f, size * 0.085f, size)
        key(canvas, "next", cx + size * 0.2f, cy + size * 0.03f, size * 0.062f, size)

        // The volume, in dots.
        val volume = when {
            muted -> context.getString(R.string.deck_muted)
            level != null -> "$level %"
            else -> "--"
        }
        centred(canvas, volume, if (muted) fonts.mono else fonts.dot, if (muted) size * 0.04f else size * 0.07f, if (muted) Nihil.MUTED else Nihil.INK, size * 0.5f, cx, cy + size * 0.19f)

        // The computer's caffeine, night light and lock.
        key(canvas, "caffeine", cx - size * 0.16f, cy + size * 0.305f, size * 0.058f, size)
        key(canvas, "night", cx, cy + size * 0.305f, size * 0.058f, size)
        key(canvas, "lock", cx + size * 0.16f, cy + size * 0.305f, size * 0.058f, size)
    }

    /** The volume as LEDs round the edge, from the top: lit to its level, unless muted. */
    private fun edge(canvas: Canvas, size: Float, cx: Float, cy: Float) {
        val radius = size / 2f - size * 0.03f
        val lit = if (muted) 0 else ((level ?: 0) * LEDS / 100f).roundToInt()
        for (i in 0 until LEDS) {
            val angle = -Math.PI / 2 + 2 * Math.PI * i / LEDS
            dots.color = if (i < lit) Nihil.INK else Nihil.OFF
            canvas.drawCircle((cx + radius * cos(angle)).toFloat(), (cy + radius * sin(angle)).toFloat(), size * 0.0075f, dots)
        }
    }

    private fun key(canvas: Canvas, name: String, x: Float, y: Float, radius: Float, size: Float) {
        keys[name] = floatArrayOf(x, y, radius)
        val down = pressed == name
        val lit = on[name] == true
        ring.strokeWidth = size * 0.006f
        ring.color = if (down || lit) Nihil.INK else Nihil.LINE
        dots.color = when {
            lit && down -> Nihil.DIM
            lit -> Nihil.INK
            down -> Nihil.RAISED
            else -> Nihil.SURFACE
        }
        canvas.drawCircle(x, y, radius, dots)
        canvas.drawCircle(x, y, radius, ring)
        val icon = icons[name]
        if (icon != null) {
            val half = (radius * 0.55f).roundToInt()
            icon.setBounds((x - half).roundToInt(), (y - half).roundToInt(), (x + half).roundToInt(), (y + half).roundToInt())
            icon.setTint(if (lit) Nihil.VOID else Nihil.INK)
            icon.draw(canvas)
            return
        }
        dots.color = Nihil.INK
        val s = radius * 0.36f
        glyph.reset()
        when (name) {
            "toggle" -> if (playing) {
                canvas.drawRect(x - s * 0.85f, y - s, x - s * 0.25f, y + s, dots)
                canvas.drawRect(x + s * 0.25f, y - s, x + s * 0.85f, y + s, dots)
            } else {
                glyph.moveTo(x - s * 0.7f, y - s)
                glyph.lineTo(x + s, y)
                glyph.lineTo(x - s * 0.7f, y + s)
            }
            "previous" -> {
                canvas.drawRect(x - s, y - s * 0.8f, x - s * 0.7f, y + s * 0.8f, dots)
                glyph.moveTo(x + s * 0.8f, y - s * 0.8f)
                glyph.lineTo(x - s * 0.55f, y)
                glyph.lineTo(x + s * 0.8f, y + s * 0.8f)
            }
            "next" -> {
                canvas.drawRect(x + s * 0.7f, y - s * 0.8f, x + s, y + s * 0.8f, dots)
                glyph.moveTo(x - s * 0.8f, y - s * 0.8f)
                glyph.lineTo(x + s * 0.55f, y)
                glyph.lineTo(x - s * 0.8f, y + s * 0.8f)
            }
        }
        glyph.close()
        canvas.drawPath(glyph, dots)
    }

    private fun centred(canvas: Canvas, text: String, typeface: android.graphics.Typeface, textSize: Float, color: Int, room: Float, cx: Float, y: Float) {
        type.typeface = typeface
        type.textSize = textSize
        type.color = color
        val shown = TextUtils.ellipsize(text, type, room, TextUtils.TruncateAt.END)
        canvas.drawText(shown, 0, shown.length, cx - type.measureText(shown, 0, shown.length) / 2f, y, type)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pressed = hit(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (hypot(event.x - downX, event.y - downY) > config.scaledTouchSlop && pressed != null) {
                pressed = null
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val key = pressed
                pressed = null
                invalidate()
                if (key != null && hit(event.x, event.y) == key) {
                    performClick()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onKey(key)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun hit(x: Float, y: Float): String? =
        keys.entries.firstOrNull { (_, key) -> hypot(x - key[0], y - key[1]) <= key[2] * 1.35f }?.key

    /** The bezel or the crown: the volume, a step at a time, each with a tick. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            val steps = turns.add(-event.getAxisValue(MotionEvent.AXIS_SCROLL)).coerceIn(-1, 1)
            if (steps != 0) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onTurn(steps)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    companion object {
        private const val LEDS = 60
        private const val STEP = 1f
        private const val SAY = 2500L
    }
}
