package com.kiddotime.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import com.kiddotime.app.data.PinRepository

class LockScreenView(
    context: Context,
    private val appName: String,
    private val onCorrectPin: () -> Unit,
    private val onWrongPin: () -> Unit,
    private val onGoHome: () -> Unit,
    private val onMaxAttempts: () -> Unit = {}
) : FrameLayout(context) {

    private val errorText = TextView(context)
    private val pinDots = mutableListOf<TextView>()
    private var currentPin = ""
    private var attemptCount = 0

    init {
        setBackgroundColor(Color.argb(245, 20, 30, 60))
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setupUI()
    }

    private fun setupUI() {
        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(60, 100, 60, 60)
        }

        container.addView(TextView(context).apply {
            text = "🔒"
            textSize = 80f
            gravity = Gravity.CENTER
        })

        container.addView(TextView(context).apply {
            text = "Time's Up!"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        })

        container.addView(TextView(context).apply {
            text = "$appName is locked.\nAsk a parent to unlock.\n\nPress back to go home."
            textSize = 17f
            setTextColor(Color.argb(200, 200, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        })

        container.addView(buildPinDisplay())

        errorText.apply {
            text = ""
            textSize = 15f
            setTextColor(Color.rgb(255, 100, 100))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }
        container.addView(errorText)
        container.addView(buildNumberPad())

        scroll.addView(container)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun buildPinDisplay(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
            for (i in 0 until 4) {
                val dot = TextView(context).apply {
                    text = "○"
                    textSize = 26f
                    setTextColor(Color.WHITE)
                    setPadding(10, 0, 10, 0)
                }
                pinDots.add(dot)
                addView(dot)
            }
        }
    }

    private fun updatePinDots() {
        pinDots.forEachIndexed { index, dot ->
            dot.text = if (index < currentPin.length) "●" else "○"
        }
    }

    private fun buildNumberPad(): LinearLayout {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            listOf("1","2","3","4","5","6","7","8","9","⌫","0","✓").forEach { label ->
                addView(Button(context).apply {
                    text = label
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.argb(100, 100, 130, 200))
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 150
                        height = 150
                        setMargins(6, 6, 6, 6)
                    }
                    setOnClickListener {
                        when (label) {
                            "⌫" -> {
                                if (currentPin.isNotEmpty()) {
                                    currentPin = currentPin.dropLast(1)
                                    updatePinDots()
                                }
                            }
                            "✓" -> verifyPin()
                            else -> {
                                if (currentPin.length < 6) {
                                    currentPin += label
                                    updatePinDots()
                                }
                            }
                        }
                    }
                })
            }
        }

        // Go home button
        val goHomeText = TextView(context).apply {
            text = "← Go to Home Screen"
            textSize = 16f
            setTextColor(Color.argb(180, 200, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
            setOnClickListener { onGoHome() }
        }

        wrapper.addView(grid)
        wrapper.addView(goHomeText)
        return wrapper
    }



    private fun verifyPin() {
        if (currentPin.isEmpty()) {
            errorText.text = "Please enter your PIN"
            return
        }
        if (PinRepository(context).verifyPin(currentPin)) {
            onCorrectPin()
        } else {
            attemptCount++
            currentPin = ""
            updatePinDots()
            if (attemptCount >= 5) {
                errorText.text = "Maximum attempts reached!"
                onMaxAttempts()
            } else {
                errorText.text = "Wrong PIN. Try again. ($attemptCount/5 attempts)"
                onWrongPin()
            }
        }
    }

    // Allow all touch events to pass through to children
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    // Do NOT override dispatchKeyEvent - let system handle back/home
}