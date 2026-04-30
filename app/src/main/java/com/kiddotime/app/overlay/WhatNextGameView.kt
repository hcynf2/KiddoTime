package com.kiddotime.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

class WhatNextGameView(context: Context) : FrameLayout(context) {

    var onActivityChosen: ((chosenLabel: String) -> Unit)? = null

    private data class Activity(val emoji: String, val label: String)

    private val activities = listOf(
        Activity("📚", "Book"),
        Activity("🧸", "Toys"),
        Activity("🍎", "Snack"),
        Activity("🛝", "Play"),
        Activity("🤗", "Cuddle")
    )

    // Guards against a second tap firing while the confirmation is showing.
    private var chosen = false

    init {
        setBackgroundColor(Color.rgb(25, 35, 75))
        buildUI()
    }

    // ── Main UI ───────────────────────────────────────────────────────────────

    private fun buildUI() {
        val scroll = ScrollView(context)
        val main = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 80, 40, 60)
        }

        main.addView(TextView(context).apply {
            text = "What's Next? 🌟"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        main.addView(TextView(context).apply {
            text = "Pick something to do next"
            textSize = 19f
            setTextColor(Color.argb(180, 200, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 44)
        })

        // Rows: 2 – 2 – 1 (last one centred)
        addRow(main, activities.subList(0, 2))
        addRow(main, activities.subList(2, 4))
        addCentredSingle(main, activities[4])

        scroll.addView(main)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun addRow(parent: LinearLayout, row: List<Activity>) {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.forEach { activity ->
            rowLayout.addView(
                buildCard(activity),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(12, 12, 12, 12)
                }
            )
        }
        parent.addView(rowLayout)
    }

    private fun addCentredSingle(parent: LinearLayout, activity: Activity) {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Equal spacers on both sides keep the card at the same width as the ones above.
        rowLayout.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        rowLayout.addView(
            buildCard(activity),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply {
                setMargins(12, 12, 12, 12)
            }
        )
        rowLayout.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        parent.addView(rowLayout)
    }

    private fun buildCard(activity: Activity): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 36, 20, 36)
            background = cardDrawable()
            elevation = 6f
            isClickable = true
            isFocusable = true

            addView(TextView(context).apply {
                text = activity.emoji
                textSize = 52f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = activity.label
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, 10, 0, 0)
            })

            setOnClickListener { onCardTapped(activity) }
        }
    }

    private fun cardDrawable() = GradientDrawable().apply {
        cornerRadius = 28f
        setColor(Color.argb(90, 100, 130, 210))
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    private fun onCardTapped(activity: Activity) {
        if (chosen) return
        chosen = true
        showConfirmation(activity)
    }

    // ── Confirmation overlay ──────────────────────────────────────────────────

    private fun showConfirmation(activity: Activity) {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(230, 18, 28, 65))
            alpha = 0f
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 0)
        }

        content.addView(TextView(context).apply {
            text = activity.emoji
            textSize = 100f
            gravity = Gravity.CENTER
        })

        content.addView(TextView(context).apply {
            text = "Next:"
            textSize = 26f
            setTextColor(Color.argb(190, 200, 220, 255))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        })

        content.addView(TextView(context).apply {
            text = activity.label
            textSize = 52f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        overlay.addView(
            content,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        overlay.animate().alpha(1f).setDuration(350).start()

        postDelayed({ onActivityChosen?.invoke(activity.label) }, 2000)
    }
}
