package com.kiddotime.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.sqrt

class CleanUpGameView(context: Context) : View(context) {

    var onAllItemsPlaced: (() -> Unit)? = null

    // ── Data ──────────────────────────────────────────────────────────────────

    private data class ToyItem(
        val emoji: String,
        var x: Float = 0f,
        var y: Float = 0f,
        var placed: Boolean = false
    )

    private data class Slot(
        val cx: Float,
        val cy: Float,
        val size: Float,
        var occupied: Boolean = false
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private val itemCount: Int = (6..10).random()
    private val items: List<ToyItem>

    private val slots = mutableListOf<Slot>()
    private var itemRadius = 0f
    private var itemsPositioned = false

    private var dragging: ToyItem? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private var allPlaced = false
    private var completionAlpha = 0f

    // ── Paint ─────────────────────────────────────────────────────────────────

    private val bgPaint = Paint()

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 52f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        textSize = 38f
    }

    private val shelfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(160, 100, 45)
        style = Paint.Style.FILL
    }

    private val shelfEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 140, 70)
        style = Paint.Style.FILL
    }

    private val slotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val slotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for shadow layers
        val pool = listOf("🧸", "🎮", "🖍️", "⚽", "🎨", "🎲", "🪀", "🎸", "🧩", "🪆")
        items = pool.shuffled().take(itemCount).map { ToyItem(it) }
        Log.d("KiddoTime", "CleanUpGameView: $itemCount items")
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        itemRadius = (w * 0.085f).coerceIn(56f, 100f)
        buildSlots(w, h)
        if (!itemsPositioned) {
            scatterItems(w, h)
            itemsPositioned = true
        }
    }

    private fun buildSlots(w: Int, h: Int) {
        slots.clear()

        // 2 rows; row 1 gets the extra item when count is odd
        val cols = ceil(itemCount / 2.0).toInt()
        val row1 = cols
        val row2 = itemCount - row1

        val shelfTop = h * SHELF_TOP_FRAC
        val shelfH   = h - shelfTop
        val slotSize = (w.toFloat() / (cols + 1)) * 0.70f
        val colStep  = w.toFloat() / (cols + 1)

        fun addRow(count: Int, rowFrac: Float) {
            val totalW = (count - 1) * colStep
            val startX = (w - totalW) / 2f
            val cy     = shelfTop + shelfH * rowFrac
            for (c in 0 until count) {
                slots.add(Slot(cx = startX + c * colStep, cy = cy, size = slotSize))
            }
        }

        addRow(row1, 0.30f)
        if (row2 > 0) addRow(row2, 0.72f)
    }

    private fun scatterItems(w: Int, h: Int) {
        val shelfTop   = h * SHELF_TOP_FRAC
        val topPad     = h * 0.20f
        val bottomBound = shelfTop - itemRadius * 2f
        val sidePad    = itemRadius * 1.6f
        val minDist    = itemRadius * 2.6f
        val placed     = mutableListOf<Pair<Float, Float>>()

        items.forEach { item ->
            var attempts = 0
            var x: Float
            var y: Float
            do {
                x = (Math.random() * (w - sidePad * 2) + sidePad).toFloat()
                y = (Math.random() * (bottomBound - topPad) + topPad).toFloat()
                attempts++
            } while (attempts < 60 && placed.any { (px, py) ->
                val dx = x - px; val dy = y - py
                sqrt(dx * dx + dy * dy) < minDist
            })
            item.x = x
            item.y = y
            placed += x to y
        }
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val shelfTop = h * SHELF_TOP_FRAC

        drawBackground(canvas, w, h)
        drawHeader(canvas, w)
        drawShelf(canvas, w, h, shelfTop)
        drawSlots(canvas)
        drawItems(canvas)
        if (allPlaced && completionAlpha > 0f) drawCompletion(canvas, w, h)
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        val gradient = LinearGradient(0f, 0f, 0f, h,
            Color.rgb(35, 50, 90), Color.rgb(15, 28, 60), Shader.TileMode.CLAMP)
        bgPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, bgPaint)
    }

    private fun drawHeader(canvas: Canvas, w: Float) {
        canvas.drawText("Clean-Up Time! 🧹", w / 2f, 90f, titlePaint)
        val placed = items.count { it.placed }
        canvas.drawText("$placed / $itemCount toys away", w / 2f, 148f, subtitlePaint)
    }

    private fun drawShelf(canvas: Canvas, w: Float, h: Float, shelfTop: Float) {
        canvas.drawRect(0f, shelfTop, w, h, shelfPaint)
        canvas.drawRect(0f, shelfTop - 14f, w, shelfTop + 10f, shelfEdgePaint)
    }

    private fun drawSlots(canvas: Canvas) {
        slots.forEach { slot ->
            val half = slot.size / 2f
            val r = RectF(slot.cx - half, slot.cy - half, slot.cx + half, slot.cy + half)
            canvas.drawRoundRect(r, 16f, 16f, slotFillPaint)
            canvas.drawRoundRect(r, 16f, 16f, slotBorderPaint)
        }
    }

    private fun drawItems(canvas: Canvas) {
        // Placed items (in slots)
        items.filter { it.placed }.forEach { drawItem(canvas, it) }
        // Floating items (not dragging)
        items.filter { !it.placed && it !== dragging }.forEach { drawItem(canvas, it) }
        // Dragging item on top
        dragging?.let { drawItem(canvas, it, dragging = true) }
    }

    private fun drawItem(canvas: Canvas, item: ToyItem, dragging: Boolean = false) {
        val r = if (dragging) itemRadius * 1.12f else itemRadius

        bubblePaint.color = if (item.placed) Color.rgb(190, 230, 190) else Color.rgb(240, 232, 215)
        bubblePaint.style = Paint.Style.FILL
        if (dragging) bubblePaint.setShadowLayer(18f, 0f, 8f, Color.argb(130, 0, 0, 0))
        else          bubblePaint.setShadowLayer(8f, 0f, 4f, Color.argb(70, 0, 0, 0))

        canvas.drawCircle(item.x, item.y, r, bubblePaint)

        emojiPaint.textSize = r * 1.05f
        canvas.drawText(item.emoji, item.x, item.y + emojiPaint.textSize * 0.35f, emojiPaint)
    }

    private fun drawCompletion(canvas: Canvas, w: Float, h: Float) {
        val overlayAlpha = (completionAlpha * 210).toInt()
        val textAlpha    = (completionAlpha * 255).toInt()

        val overlayPaint = Paint().apply { color = Color.argb(overlayAlpha, 18, 35, 70) }
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(textAlpha, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((textAlpha * 0.8f).toInt(), 200, 230, 255)
            textAlign = Paint.Align.CENTER
            textSize = 44f
        }

        canvas.drawText("🎉 All Clean! 🎉", w / 2f, h / 2f - 60f, msgPaint)
        canvas.drawText("Time to take a break!", w / 2f, h / 2f + 20f, subPaint)
    }

    // ── Touch / drag ──────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (allPlaced) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val tx = event.x; val ty = event.y
                val hit = items
                    .filter { !it.placed }
                    .minByOrNull { item ->
                        val dx = tx - item.x; val dy = ty - item.y
                        sqrt(dx * dx + dy * dy)
                    }
                    ?.takeIf { item ->
                        val dx = tx - item.x; val dy = ty - item.y
                        sqrt(dx * dx + dy * dy) <= itemRadius * 1.6f
                    }

                if (hit != null) {
                    dragging = hit
                    dragOffX = hit.x - tx
                    dragOffY = hit.y - ty
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                dragging?.let { item ->
                    item.x = event.x + dragOffX
                    item.y = event.y + dragOffY
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging?.let { item ->
                    val shelfTop = height * SHELF_TOP_FRAC
                    val isOverShelf = item.y >= shelfTop - itemRadius

                    if (isOverShelf) {
                        val nearestEmpty = slots
                            .filter { !it.occupied }
                            .minByOrNull { slot ->
                                val dx = item.x - slot.cx; val dy = item.y - slot.cy
                                sqrt(dx * dx + dy * dy)
                            }
                        if (nearestEmpty != null) {
                            item.x = nearestEmpty.cx
                            item.y = nearestEmpty.cy
                            item.placed = true
                            nearestEmpty.occupied = true
                            Log.d("KiddoTime", "CleanUp: placed ${item.emoji} (${items.count { it.placed }}/$itemCount)")
                            if (items.all { it.placed }) onAllPlacedComplete()
                        }
                    }
                    dragging = null
                    invalidate()
                }
            }
        }
        return true
    }

    // ── Completion ────────────────────────────────────────────────────────────

    private fun onAllPlacedComplete() {
        allPlaced = true
        Log.d("KiddoTime", "CleanUpGameView: all items placed — showing completion")

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener {
                if (isAttachedToWindow) {
                    completionAlpha = it.animatedValue as Float
                    invalidate()
                }
            }
            start()
        }

        postDelayed({
            if (isAttachedToWindow) onAllItemsPlaced?.invoke()
        }, 2500)
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val SHELF_TOP_FRAC = 0.60f
    }
}
