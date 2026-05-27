package com.rpsonline.app.domain

/**
 * Series format for ranked matches. Values are generated from [shared/game-rules.json].
 */
enum class MatchMode(
    val winsToFinish: Int,
    val bestOfRounds: Int,
    val tiedSeriesScore: Int? = null,
) {
    BO3(
        winsToFinish = GeneratedGameRules.Mode.BO3.winsToFinish,
        bestOfRounds = GeneratedGameRules.Mode.BO3.bestOfRounds,
    ),
    BO5(
        winsToFinish = GeneratedGameRules.Mode.BO5.winsToFinish,
        bestOfRounds = GeneratedGameRules.Mode.BO5.bestOfRounds,
    ),
    BO10(
        winsToFinish = GeneratedGameRules.Mode.BO10.winsToFinish,
        bestOfRounds = GeneratedGameRules.Mode.BO10.bestOfRounds,
        tiedSeriesScore = GeneratedGameRules.Mode.BO10.tiedSeriesScore,
    ),
    ;

    val label: String get() = "Best of $bestOfRounds"

    companion object {
        val DEFAULT_SELECTION: Set<MatchMode> = entries.toSet()

        fun fromString(value: String?): MatchMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BO3

        fun parseStoredNames(names: Set<String>?): Set<MatchMode> {
            if (names.isNullOrEmpty()) return DEFAULT_SELECTION
            return names.mapNotNull { name -> entries.find { it.name.equals(name, ignoreCase = true) } }
                .toSet()
                .ifEmpty { DEFAULT_SELECTION }
        }

        fun parseRouteArg(value: String?): Set<MatchMode> {
            if (value.isNullOrBlank()) return DEFAULT_SELECTION
            return value.split(",")
                .mapNotNull { part -> entries.find { it.name.equals(part.trim(), ignoreCase = true) } }
                .toSet()
                .ifEmpty { DEFAULT_SELECTION }
        }

        fun encodeRouteArg(modes: Set<MatchMode>): String =
            modes.sortedBy { it.ordinal }.joinToString(",") { it.name }

        fun toggleInSelection(current: Set<MatchMode>, mode: MatchMode): Set<MatchMode> =
            when {
                mode !in current -> current + mode
                current.size == 1 -> entries.filter { it != mode }.toSet()
                else -> current - mode
            }
    }
}
