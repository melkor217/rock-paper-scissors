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
        val DEFAULT_SELECTION: Set<MatchMode> = setOf(BO3)

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
    }
}
