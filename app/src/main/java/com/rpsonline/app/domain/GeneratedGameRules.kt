package com.rpsonline.app.domain

// Generated from shared/game-rules.json — run ./scripts/sync-game-rules.sh after edits.
object GeneratedGameRules {
    const val ROUND_TIMEOUT_SECONDS: Int = 60
    const val INITIAL_CLOCK_MS: Long = 50000L
    const val MAX_CLOCK_MS: Long = 90000L
    const val CLOCK_INCREMENT_MS: Long = 5000L
    const val CLOCK_RING_FULL_SECONDS: Int = 60

    enum class Mode(
        val winsToFinish: Int,
        val bestOfRounds: Int,
        val tiedSeriesScore: Int? = null,
    ) {
        BO3(winsToFinish = 2, bestOfRounds = 3),
        BO5(winsToFinish = 3, bestOfRounds = 5),
        BO10(winsToFinish = 6, bestOfRounds = 10, tiedSeriesScore = 5),
    }
}
