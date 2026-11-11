package com.nihilshell.wear.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The home on the watch: a list the bezel or the crown turns, or a finger
 * drags. What stands in the middle is lit in ink and the rest stays grey,
 * fading towards the edge, as the desktop's apps stay grey until they are
 * chosen; each turn past one ticks. A tap opens what it touches. Over it, the
 * time in dots.
 */
class AppList(context: Context) : View(context) {
    /** One line: [led] is a colour for the LED before it, or 0 for none. */
    data class Entry(val key: String, val title: String, val note: String? = null, val led: Int = 0)

    var onOpen: (Entry) -> Unit = {}
    var entries: List<Entry> = emptyList()
        set(value) {
            field = value
            offset = offset.coerceIn(0f, limit())
            invalidate()
        }

    private val fonts = Nihil.fonts(context)
    private val type = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scroller = OverScroller(context)
    private val config = ViewConfiguration.get(context)
    private var velocity: VelocityTracker? = null
    private var offset = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var flinging = false
    private var centred = 0
    private val settle = Runnable { snap() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Nihil.VOID)
    }

    private val size: Float get() = min(width, height).toFloat()
    private val row: Float get() = size * ROW
    private val middle: Float get() = height / 2f + size * 0.05f

    private fun limit(): Float = max(0f, (entries.size - 1) * row)

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val cx = width / 2f
        val radius = size / 2f
        for ((i, entry) in entries.withIndex()) {
            val y = middle + i * row - offset
            if (y < -row || y > height + row) continue
            val distance = abs(y - middle) / row
            val near = (1f - distance).coerceIn(0f, 1f)
            val textSize = size * (0.058f + 0.02f * near)
            val alpha = (255 * (1f - distance / 3.4f)).roundToInt().coerceIn(40, 255)
            // As wide as the round screen is at that height.
            val half = sqrt(max(0f, radius * radius - (y - height / 2f) * (y - height / 2f)))
            val room = max(0f, min(half * 2f - size * 0.16f, size * 0.72f))
            type.typeface = if (near > 0.5f) fonts.sansMedium else fonts.sans
            type.textSize = textSize
            type.color = if (near > 0.5f) Nihil.INK else Nihil.DIM
            type.alpha = alpha
            val shown = TextUtils.ellipsize(entry.title, type, room - (if (entry.led != 0) size * 0.05f else 0f), TextUtils.TruncateAt.END)
            val width = type.measureText(shown, 0, shown.length)
            val led = if (entry.led != 0) size * 0.05f else 0f
            val left = cx - (width + led) / 2f
            if (entry.led != 0) {
                dots.color = entry.led
                dots.alpha = alpha
                canvas.drawCircle(left + size * 0.012f, y - textSize * 0.32f, size * 0.012f, dots)
            }
            canvas.drawText(shown, 0, shown.length, left + led, y, type)
            if (entry.note != null && near > 0.5f) {
                type.typeface = fonts.mono
                type.textSize = size * 0.036f
                type.color = Nihil.MUTED
                type.alpha = (255 * (near - 0.5f) * 2f).roundToInt().coerceIn(0, 255)
                val note = TextUtils.ellipsize(entry.note, type, room, TextUtils.TruncateAt.END)
                canvas.drawText(note, 0, note.length, cx - type.measureText(note, 0, note.length) / 2f, y + size * 0.05f, type)
            }
        }
        clock(canvas, cx)
    }

    /** The time, in dots, at the top. */
    private fun clock(canvas: Canvas, cx: Float) {
        val time = DateFormat.getTimeFormat(context).format(Date())
        type.typeface = fonts.dot
        type.textSize = size * 0.068f
        type.color = Nihil.DIM
        type.alpha = 255
        canvas.drawText(time, cx - type.measureText(time) / 2f, size * 0.14f, type)
        val now = System.currentTimeMillis()
        postInvalidateDelayed(60_000 - now % 60_000 + 50)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tracker = velocity ?: VelocityTracker.obtain().also { velocity = it }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                flinging = false
                downY = event.y
                lastY = event.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.y - downY) > config.scaledTouchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    move(lastY - event.y)
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    tracker.computeCurrentVelocity(1000, config.scaledMaximumFlingVelocity.toFloat())
                    val speed = -tracker.yVelocity
                    if (abs(speed) > config.scaledMinimumFlingVelocity) fling(speed) else snap()
                } else {
                    tap(event.y)
                }
                done()
            }
            MotionEvent.ACTION_CANCEL -> {
                done()
                snap()
            }
        }
        return true
    }

    private fun done() {
        velocity?.recycle()
        velocity = null
        dragging = false
    }

    private fun tap(y: Float) {
        val index = ((y - middle + offset) / row).roundToInt()
        val entry = entries.getOrNull(index) ?: return
        performClick()
        onOpen(entry)
    }

    override fun performClick(): Boolean = super.performClick()

    /** The crown, a rotating bezel or a touch bezel: Wear OS gives them all as the same scroll. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            scroller.forceFinished(true)
            flinging = false
            move(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * config.scaledVerticalScrollFactor)
            removeCallbacks(settle)
            postDelayed(settle, SETTLE)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun move(dy: Float) {
        offset = (offset + dy).coerceIn(0f, limit())
        tick()
        invalidate()
    }

    private fun fling(speed: Float) {
        flinging = true
        scroller.fling(0, offset.roundToInt(), 0, speed.roundToInt(), 0, 0, 0, limit().roundToInt())
        postInvalidateOnAnimation()
    }

    /** To the nearest line, so one always stands in the middle. */
    private fun snap() {
        if (row <= 0f) return
        val target = ((offset / row).roundToInt() * row).coerceIn(0f, limit())
        scroller.startScroll(0, offset.roundToInt(), 0, (target - offset).roundToInt(), Nihil.NORMAL.toInt())
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offset = scroller.currY.toFloat().coerceIn(0f, limit())
            tick()
            postInvalidateOnAnimation()
        } else if (flinging) {
            flinging = false
            snap()
        }
    }

    private fun tick() {
        if (row <= 0f) return
        val now = (offset / row).roundToInt()
        if (now != centred) {
            centred = now
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    companion object {
        private const val ROW = 0.15f
        private const val SETTLE = 140L
    }
}
