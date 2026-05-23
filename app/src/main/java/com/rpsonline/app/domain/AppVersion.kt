package com.rpsonline.app.domain

/** Matches CI tag → versionCode mapping in `.github/workflows/android-release.yml`. */
fun versionCodeFromTag(tag: String): Int {
    val version = tag.trim().removePrefix("v").removePrefix("V")
    val parts = version.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

fun versionLabelFromTag(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")
