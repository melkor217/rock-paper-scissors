package com.rpsonline.app.domain

/**
 * Series format for ranked matches. [winsToFinish] must stay aligned with Cloud Functions.
 */
enum class MatchMode(
    val winsToFinish: Int,
    val bestOfRounds: Int,
    val label: String,
) {
    BO3(winsToFinish = 2, bestOfRounds = 3, label = "Best of 3"),
    BO5(winsToFinish = 3, bestOfRounds = 5, label = "Best of 5"),
    ;

    companion object {
        fun fromString(value: String?): MatchMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BO3
    }
}
