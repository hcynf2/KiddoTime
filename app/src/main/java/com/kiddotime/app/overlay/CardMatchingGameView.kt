package com.kiddotime.app.overlay

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.util.Log

data class Card(
    val id: Int,
    val emoji: String,
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false,
    var flipProgress: Float = 0f, // 0 = face down, 1 = face up
    var rect: RectF = RectF()
)

class CardMatchingGameView(context: Context) : View(context) {

    // Callbacks
    var onAllRoundsComplete: (() -> Unit)? = null

    // Game state
    private var cards = mutableListOf<Card>()
    private var firstCard: Card? = null
    private var secondCard: Card? = null
    private var isChecking = false
    private var currentRound = 1
    private val totalRounds = 3
    private var matchesFound = 0
    private var showingRoundComplete = false
    private var roundCompleteAlpha = 0f

    // Emoji sets per round - simple and recognizable for under-5s
    private val emojiSets = listOf(
        listOf("🐞", "🐱", "🐭", "🐸"),  // Round 1: animals
        listOf("🍎", "🍌", "🍓", "🍊"),  // Round 2: fruits
        listOf("⭐", "🌙", "☀️", "🌈")   // Round 3: sky
    )

    // Paint objects
    private val cardBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardFacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 80f
    }
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
    private val matchedOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 100, 220, 100)
    }

    // Sound
    private var soundPool: SoundPool? = null
    private var flipSoundId = 0
    private var matchSoundId = 0
    private var successSoundId = 0

    init {
        setupSounds()
        setupRound(currentRound)
    }

    private fun setupSounds() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // Generate tones programmatically since we have no assets
        soundPool?.setOnLoadCompleteListener { _, _, _ -> }
    }

    private fun playTone(frequency: Float, duration: Int) {
        // Simple tone via AudioTrack
        val sampleRate = 44100
        val numSamples = duration * sampleRate / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
            val envelope = when {
                i < numSamples * 0.1 -> i / (numSamples * 0.1)
                i > numSamples * 0.8 -> (numSamples - i) / (numSamples * 0.2)
                else -> 1.0
            }
            buffer[i] = (Math.sin(angle) * envelope * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        Thread {
            try {
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                    .build()
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(duration.toLong())
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Silently fail if audio unavailable
            }
        }.start()
    }

    private fun playFlipSound() = playTone(440f, 80)
    private fun playMatchSound() {
        playTone(523f, 100)
        postDelayed({ playTone(659f, 100) }, 120)
        postDelayed({ playTone(784f, 150) }, 250)
    }
    private fun playSuccessSound() {
        playTone(523f, 100)
        postDelayed({ playTone(659f, 100) }, 120)
        postDelayed({ playTone(784f, 100) }, 240)
        postDelayed({ playTone(1047f, 300) }, 360)
    }

    private fun setupRound(round: Int) {
        cards.clear()
        firstCard = null
        secondCard = null
        isChecking = false
        matchesFound = 0
        showingRoundComplete = false

        val emojis = emojiSets[(round - 1).coerceIn(0, emojiSets.size - 1)]
        val cardEmojis = (emojis + emojis).shuffled()

        cards.addAll(cardEmojis.mapIndexed { index, emoji ->
            Card(id = index, emoji = emoji)
        })

        postDelayed({ layoutCards() }, 100)
        invalidate()
    }

    private fun layoutCards() {
        if (width == 0 || height == 0) {
            postDelayed({ layoutCards() }, 100)
            return
        }

        val cols = 4
        val rows = 2
        val padding = 32f
        val topOffset = 220f
        val cardWidth = (width - padding * (cols + 1)) / cols
        val cardHeight = (height - topOffset - padding * (rows + 1) - 100f) / rows

        cards.forEachIndexed { index, card ->
            val col = index % cols
            val row = index / cols
            val left = padding + col * (cardWidth + padding)
            val top = topOffset + padding + row * (cardHeight + padding)
            card.rect = RectF(left, top, left + cardWidth, top + cardHeight)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCards()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background gradient
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.rgb(30, 40, 80),
            Color.rgb(10, 20, 50),
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Title
        canvas.drawText("Find the Matches! 🎴", width / 2f, 100f, titlePaint)

        // Round indicator
        canvas.drawText(
            "Round $currentRound of $totalRounds",
            width / 2f, 160f, subtitlePaint
        )

        // Progress dots
        drawProgressDots(canvas)

        // Draw cards
        cards.forEach { card -> drawCard(canvas, card) }

        // Round complete overlay
        if (showingRoundComplete) {
            drawRoundComplete(canvas)
        }
    }

    private fun drawProgressDots(canvas: Canvas) {
        val dotRadius = 12f
        val spacing = 40f
        val totalWidth = (totalRounds - 1) * spacing
        val startX = width / 2f - totalWidth / 2f

        for (i in 1..totalRounds) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (i < currentRound) Color.rgb(100, 220, 100)
                else if (i == currentRound) Color.WHITE
                else Color.argb(100, 255, 255, 255)
            }
            canvas.drawCircle(startX + (i - 1) * spacing, 200f, dotRadius, paint)
        }
    }

    private fun drawCard(canvas: Canvas, card: Card) {
        val rect = card.rect
        val cornerRadius = 24f

        if (card.flipProgress < 0.5f) {
            // Draw card back
            val scaleX = 1f - card.flipProgress * 2f
            val cx = rect.centerX()
            canvas.save()
            canvas.scale(scaleX, 1f, cx, rect.centerY())

            cardBackPaint.apply {
                color = Color.rgb(70, 100, 180)
                style = Paint.Style.FILL
                setShadowLayer(8f, 0f, 4f, Color.argb(100, 0, 0, 0))
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardBackPaint)

            // Pattern on back
            val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(
                RectF(rect.left + 12, rect.top + 12, rect.right - 12, rect.bottom - 12),
                16f, 16f, patternPaint
            )
            canvas.drawText("?", rect.centerX(), rect.centerY() + 28f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 255, 255, 255)
                    textSize = 64f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
            )
            canvas.restore()

        } else {
            // Draw card face
            val scaleX = (card.flipProgress - 0.5f) * 2f
            val cx = rect.centerX()
            canvas.save()
            canvas.scale(scaleX, 1f, cx, rect.centerY())

            cardFacePaint.apply {
                color = Color.rgb(240, 245, 255)
                style = Paint.Style.FILL
                setShadowLayer(8f, 0f, 4f, Color.argb(100, 0, 0, 0))
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardFacePaint)

            // Matched overlay
            if (card.isMatched) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, matchedOverlayPaint)
            }

            // Emoji
            textPaint.textSize = minOf(rect.width(), rect.height()) * 0.45f
            canvas.drawText(card.emoji, rect.centerX(), rect.centerY() + textPaint.textSize * 0.35f, textPaint)

            canvas.restore()
        }
    }

    private fun drawRoundComplete(canvas: Canvas) {
        val overlayPaint = Paint().apply {
            color = Color.argb((roundCompleteAlpha * 180).toInt(), 20, 30, 60)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        val alpha = (roundCompleteAlpha * 255).toInt()
        val msgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alpha * 0.8f).toInt(), 200, 230, 255)
            textAlign = Paint.Align.CENTER
            textSize = 44f
        }

        if (currentRound > totalRounds) {
            canvas.drawText("🎉 Well Done! 🎉", width / 2f, height / 2f - 60f, msgPaint)
            canvas.drawText("Time to take a break!", width / 2f, height / 2f + 20f, subPaint)
        } else {
            canvas.drawText("⭐ Great Job! ⭐", width / 2f, height / 2f - 60f, msgPaint)
            canvas.drawText("Round $currentRound coming up!", width / 2f, height / 2f + 20f, subPaint)
        }
    }

    private fun flipCard(card: Card, faceUp: Boolean) {
        val start = if (faceUp) 0f else 1f
        val end = if (faceUp) 1f else 0f
        ValueAnimator.ofFloat(start, end).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                card.flipProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        playFlipSound()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (isChecking || showingRoundComplete) return true

        val touchX = event.x
        val touchY = event.y

        val tappedCard = cards.firstOrNull { card ->
            !card.isFaceUp && !card.isMatched && card.rect.contains(touchX, touchY)
        } ?: return true

        flipCard(tappedCard, faceUp = true)
        tappedCard.isFaceUp = true

        when {
            firstCard == null -> {
                firstCard = tappedCard
            }
            secondCard == null && tappedCard.id != firstCard?.id -> {
                secondCard = tappedCard
                isChecking = true
                postDelayed(::checkMatch, 800)
            }
        }
        return true
    }

    private fun checkMatch() {
        val first = firstCard ?: return
        val second = secondCard ?: return

        if (first.emoji == second.emoji) {
            // Match found!
            first.isMatched = true
            second.isMatched = true
            matchesFound++
            playMatchSound()

            if (matchesFound == emojiSets[(currentRound - 1).coerceIn(0, emojiSets.size - 1)].size) {
                // Round complete
                postDelayed(::onRoundComplete, 600)
            }
        } else {
            // No match - flip back
            flipCard(first, faceUp = false)
            flipCard(second, faceUp = false)
            first.isFaceUp = false
            second.isFaceUp = false
        }

        firstCard = null
        secondCard = null
        isChecking = false
        invalidate()
    }

    private fun onRoundComplete() {
        currentRound++
        Log.d("KiddoTime", "onRoundComplete called - currentRound=$currentRound totalRounds=$totalRounds")
        showingRoundComplete = true
        playSuccessSound()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener {
                if (isAttachedToWindow) {
                    roundCompleteAlpha = it.animatedValue as Float
                    invalidate()
                }
            }
            start()
        }

        if (currentRound > totalRounds) {
            Log.d("KiddoTime", "All rounds complete - calling onAllRoundsComplete")
            postDelayed({
                if (isAttachedToWindow) onAllRoundsComplete?.invoke()
            }, 2500)
        } else {
            postDelayed({
                if (isAttachedToWindow) {
                    showingRoundComplete = false
                    setupRound(currentRound)
                }
            }, 2500)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool?.release()
        soundPool = null
    }
}