package com.rpsonline.app.domain

import com.rpsonline.app.data.model.Move

object GameRules {
    const val ROUND_TIMEOUT_SECONDS = GeneratedGameRules.ROUND_TIMEOUT_SECONDS
    const val INITIAL_CLOCK_MS = GeneratedGameRules.INITIAL_CLOCK_MS
    const val CLOCK_INCREMENT_MS = GeneratedGameRules.CLOCK_INCREMENT_MS

    /**
     * Returns winner move on victory, null on tie.
     */
    fun resolveRound(player1: Move, player2: Move): Move? {
        if (player1 == player2) return null
        return when {
            player1 == Move.ROCK && player2 == Move.SCISSORS -> Move.ROCK
            player1 == Move.PAPER && player2 == Move.ROCK -> Move.PAPER
            player1 == Move.SCISSORS && player2 == Move.PAPER -> Move.SCISSORS
            player2 == Move.ROCK && player1 == Move.SCISSORS -> Move.ROCK
            player2 == Move.PAPER && player1 == Move.ROCK -> Move.PAPER
            player2 == Move.SCISSORS && player1 == Move.PAPER -> Move.SCISSORS
            else -> null
        }
    }

    fun beats(winner: Move, loser: Move): Boolean = resolveRound(winner, loser) == winner
}
