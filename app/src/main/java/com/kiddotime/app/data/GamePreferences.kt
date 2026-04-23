package com.kiddotime.app.data

import android.content.Context

/**
 * Tracks which mini-game to show next.
 *
 * Rules:
 * - If a game was interrupted (overlay closed before completion), [currentGame] is still set
 *   and [pickGame] returns it so the child resumes the same game.
 * - Otherwise, [pickGame] picks randomly from all games except [lastGame], so the same game
 *   is never shown twice in a row.  On the very first pick any game may be chosen.
 * - [onGameComplete] must be called after the game finishes so [currentGame] is cleared and
 *   [lastGame] is updated.
 */
class GamePreferences(context: Context) {

    private val prefs = context.getSharedPreferences("kiddotime_game_prefs", Context.MODE_PRIVATE)

    private var lastGame: String
        get() = prefs.getString(KEY_LAST, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LAST, v).apply() }

    private var currentGame: String
        get() = prefs.getString(KEY_CURRENT, "") ?: ""
        set(v) { prefs.edit().putString(KEY_CURRENT, v).apply() }

    /** Returns the game to show, persisting the choice for resume support. */
    fun pickGame(): String {
        val inProgress = currentGame
        if (inProgress.isNotEmpty()) return inProgress

        val all = listOf(GAME_CARD, GAME_CLEANUP, GAME_WHAT_NEXT)
        val candidates = all.filter { it != lastGame }.ifEmpty { all }
        val next = candidates.random()
        currentGame = next
        return next
    }

    /** Call when the game completes successfully (before showing the lock screen). */
    fun onGameComplete(game: String) {
        lastGame = game
        currentGame = ""
    }

    companion object {
        const val GAME_CARD      = "card"
        const val GAME_CLEANUP   = "cleanup"
        const val GAME_WHAT_NEXT = "whatnext"

        private const val KEY_LAST    = "last_game"
        private const val KEY_CURRENT = "current_game"
    }
}
