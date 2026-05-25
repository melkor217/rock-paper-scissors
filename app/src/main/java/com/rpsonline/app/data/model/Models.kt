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
    val player1Elo: Int? = null,
    val player2Elo: Int? = null,
    val createdAt: Long = 0L,
    val lastActivityAt: Long = 0L,
) {
    fun isParticipant(userId: String): Boolean =
        userId == player1 || userId == player2

    fun opponentName(userId: String): String =
        if (userId == player1) player2Name else player1Name

    fun myName(userId: String): String =
        if (userId == player1) player1Name else player2Name

    fun opponentId(userId: String): String =
        if (userId == player1) player2 else player1

    fun myWins(userId: String): Int =
        if (userId == player1) player1Wins else player2Wins

    fun opponentWins(userId: String): Int =
        if (userId == player1) player2Wins else player1Wins

    fun myEloDelta(userId: String): Int? =
        if (userId == player1) player1EloDelta else player2EloDelta

    fun opponentElo(userId: String): Int? =
        if (userId == player1) player2Elo else player1Elo

    fun myElo(userId: String): Int? =
        if (userId == player1) player1Elo else player2Elo

    fun currentRoundData(): RoundResult? =
        rounds.filter { it.resolvedAt == null }.lastOrNull()
            ?: rounds.find { it.roundNumber == currentRound }

    fun lastResolvedRound(): RoundResult? =
        rounds
            .filter { it.resolvedAt != null }
            .maxByOrNull { it.resolvedAt ?: 0L }

    /** Last round was a tie; next round is open (score unchanged). */
    fun pendingDrawReplay(): RoundResult? {
        val last = lastResolvedRound() ?: return null
        if (last.winner != "tie") return null
        val open = openRound() ?: return null
        return if (open.roundNumber > last.roundNumber) last else null
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

    fun resolvedRoundRecaps(userId: String): List<RoundRecap> =
        rounds
            .filter { it.resolvedAt != null }
            .sortedWith(compareBy({ it.resolvedAt ?: 0L }, { it.roundNumber }))
            .map { round ->
                val myChoice = if (userId == player1) round.player1Choice else round.player2Choice
                val opponentChoice = if (userId == player1) round.player2Choice else round.player1Choice
                val isDraw = round.winner == "tie"
                val won = when (round.winner) {
                    "tie" -> null
                    userId -> true
                    else -> false
                }
                RoundRecap(
                    roundNumber = round.roundNumber,
                    myChoice = myChoice,
                    opponentChoice = opponentChoice,
                    won = won,
                    isDraw = isDraw,
                    opponentTimedOut = !isDraw && won == true && opponentChoice == null,
                    iTimedOut = !isDraw && won == false && myChoice == null,
                )
            }
}

data class RoundRecap(
    val roundNumber: Int,
    val myChoice: String?,
    val opponentChoice: String?,
    val won: Boolean?,
    val isDraw: Boolean = false,
    val opponentTimedOut: Boolean = false,
    val iTimedOut: Boolean = false,
)

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val elo: Int = 1000,
    val wins: Int = 0,
    val losses: Int = 0,
    val throwsRock: Int = 0,
    val throwsPaper: Int = 0,
    val throwsScissors: Int = 0,
    val activeMatchId: String? = null,
)

data class LeaderboardEntry(
    val uid: String = "",
    val displayName: String = "",
    val elo: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val throwsRock: Int = 0,
    val throwsPaper: Int = 0,
    val throwsScissors: Int = 0,
)

data class MatchResult(
    val matchId: String,
    val won: Boolean,
    val eloDelta: Int,
    val myWins: Int,
    val opponentWins: Int,
    val opponentName: String,
)

data class MatchHistoryEntry(
    val matchId: String,
    val myDisplayName: String,
    val opponentName: String,
    val myElo: Int? = null,
    val opponentElo: Int? = null,
    val myWins: Int,
    val opponentWins: Int,
    val won: Boolean?,
    val isDraw: Boolean,
    val isAbandoned: Boolean,
    val eloDelta: Int?,
    val lastActivityAt: Long,
    val recaps: List<RoundRecap>,
)

fun Match.toHistoryEntry(userId: String): MatchHistoryEntry {
    val myWins = myWins(userId)
    val opponentWins = opponentWins(userId)
    val abandoned = status == MatchStatus.ABANDONED
    val draw = !abandoned && winnerId == null && myWins == opponentWins
    val won = when {
        abandoned -> null
        draw -> null
        winnerId == userId -> true
        else -> false
    }
    return MatchHistoryEntry(
        matchId = id,
        myDisplayName = myName(userId),
        opponentName = opponentName(userId),
        myElo = myElo(userId),
        opponentElo = opponentElo(userId),
        myWins = myWins,
        opponentWins = opponentWins,
        won = won,
        isDraw = draw,
        isAbandoned = abandoned,
        eloDelta = myEloDelta(userId),
        lastActivityAt = lastActivityAt,
        recaps = resolvedRoundRecaps(userId),
    )
}
