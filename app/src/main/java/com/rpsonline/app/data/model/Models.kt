package com.rpsonline.app.data.model

enum class Move(val label: String) {
    ROCK("Rock"),
    PAPER("Paper"),
    SCISSORS("Scissors");

    companion object {
        fun fromString(value: String?): Move? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}

enum class MatchStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED;

    companion object {
        fun fromString(value: String?): MatchStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ACTIVE
    }
}

data class RoundResult(
    val roundNumber: Int = 0,
    val player1Choice: String? = null,
    val player2Choice: String? = null,
    val winner: String? = null,
    val resolvedAt: Long? = null,
    val deadline: Long? = null,
)

data class Match(
    val id: String = "",
    val player1: String = "",
    val player2: String = "",
    val player1Name: String = "",
    val player2Name: String = "",
    val status: MatchStatus = MatchStatus.ACTIVE,
    val currentRound: Int = 1,
    val player1Wins: Int = 0,
    val player2Wins: Int = 0,
    val rounds: List<RoundResult> = emptyList(),
    val winnerId: String? = null,
    val player1EloDelta: Int? = null,
    val player2EloDelta: Int? = null,
    val createdAt: Long = 0L,
    val lastActivityAt: Long = 0L,
) {
    fun isParticipant(userId: String): Boolean =
        userId == player1 || userId == player2

    fun opponentName(userId: String): String =
        if (userId == player1) player2Name else player1Name

    fun myWins(userId: String): Int =
        if (userId == player1) player1Wins else player2Wins

    fun opponentWins(userId: String): Int =
        if (userId == player1) player2Wins else player1Wins

    fun myEloDelta(userId: String): Int? =
        if (userId == player1) player1EloDelta else player2EloDelta

    fun currentRoundData(): RoundResult? =
        rounds.filter { it.resolvedAt == null }.lastOrNull()
            ?: rounds.find { it.roundNumber == currentRound }

    fun lastResolvedRound(): RoundResult? =
        rounds
            .filter { it.resolvedAt != null }
            .maxByOrNull { it.resolvedAt ?: 0L }

    /** Last round was a tie and we're replaying the same round number (score unchanged). */
    fun pendingDrawReplay(): RoundResult? {
        val last = lastResolvedRound() ?: return null
        if (last.winner != "tie") return null
        val hasOpenRound = rounds.any { it.resolvedAt == null && it.roundNumber == currentRound }
        return if (hasOpenRound) last else null
    }

    fun openRound(): RoundResult? =
        rounds.filter { it.resolvedAt == null }.lastOrNull()

    /** Last round had a winner; next round is open — keep showing outcome until play resumes. */
    fun pendingRoundOutcome(): RoundResult? {
        val last = lastResolvedRound() ?: return null
        if (last.winner == "tie" || last.winner == null) return null
        if (last.player1Choice == null || last.player2Choice == null) return null
        val open = openRound() ?: return null
        return if (open.roundNumber > last.roundNumber) last else null
    }
}

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val elo: Int = 1000,
    val wins: Int = 0,
    val losses: Int = 0,
    val activeMatchId: String? = null,
)

data class LeaderboardEntry(
    val uid: String = "",
    val displayName: String = "",
    val elo: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
)

data class MatchResult(
    val matchId: String,
    val won: Boolean,
    val eloDelta: Int,
    val myWins: Int,
    val opponentWins: Int,
    val opponentName: String,
)
