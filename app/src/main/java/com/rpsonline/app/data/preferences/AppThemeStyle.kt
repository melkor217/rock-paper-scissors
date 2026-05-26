package com.rpsonline.app.data.preferences

enum class AppThemeStyle(
    val id: String,
    val label: String,
    val isDark: Boolean,
) {
    CYBERPUNK("cyberpunk", "Cyberpunk", isDark = true),
    LIGHT("light", "Light", isDark = false),
    DARK("dark", "Dark", isDark = true),
    COSMOS("cosmos", "Cosmos", isDark = true),
    FIRE("fire", "Fire", isDark = true),
    ;

    companion object {
        val default: AppThemeStyle = CYBERPUNK

        fun fromId(id: String?): AppThemeStyle =
            entries.firstOrNull { it.id == id } ?: default
    }
}
