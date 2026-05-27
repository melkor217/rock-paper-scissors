package com.rpsonline.app.data.model

/** How a finished match was resolved (stored on the match document). */
// Server-side canonical inference lives in functions/src/game.ts (`inferMatchResolution`).
enum class MatchResolution {
    PLAYER1_WIN,
    PLAYER2_WIN,
    DRAW,
    ABANDONED,
    ;

    companion object {
        fun fromString(value: String?): MatchResolution? = when (value?.lowercase()) {
            "player1_win" -> PLAYER1_WIN
            "player2_win" -> PLAYER2_WIN
            "draw" -> DRAW
            "abandoned" -> ABANDONED
            else -> null
        }

        fun firestoreValue(resolution: MatchResolution): String = when (resolution) {
            PLAYER1_WIN -> "player1_win"
            PLAYER2_WIN -> "player2_win"
            DRAW -> "draw"
            ABANDONED -> "abandoned"
        }
    }
}

/** Match outcome from a specific player's perspective (for history, result screen, etc.). */
enum class ViewerMatchResolution {
    WIN,
    LOSS,
    DRAW,
    ABANDONED,
}

fun Match.resolvedOutcome(): MatchResolution? =
    resolution ?: inferResolutionFromLegacyFields()

fun Match.viewerResolution(userId: String): ViewerMatchResolution? =
    resolvedOutcome()?.forViewer(userId, player1, player2)

fun MatchResolution.forViewer(
    viewerId: String,
    player1: String,
    player2: String,
): ViewerMatchResolution = when (this) {
    MatchResolution.ABANDONED -> ViewerMatchResolution.ABANDONED
    MatchResolution.DRAW -> ViewerMatchResolution.DRAW
    MatchResolution.PLAYER1_WIN -> if (viewerId == player1) {
        ViewerMatchResolution.WIN
    } else {
        ViewerMatchResolution.LOSS
    }
    MatchResolution.PLAYER2_WIN -> if (viewerId == player2) {
        ViewerMatchResolution.WIN
    } else {
        ViewerMatchResolution.LOSS
    }
}

private fun Match.inferResolutionFromLegacyFields(): MatchResolution? = when (status) {
    MatchStatus.ABANDONED -> MatchResolution.ABANDONED
    MatchStatus.COMPLETED -> when (winnerId) {
        null -> if (player1Wins == player2Wins) MatchResolution.DRAW else null
        player1 -> MatchResolution.PLAYER1_WIN
        player2 -> MatchResolution.PLAYER2_WIN
        else -> null
    }
    MatchStatus.ACTIVE -> null
}
