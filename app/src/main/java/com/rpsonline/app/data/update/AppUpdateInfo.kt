package com.rpsonline.app.data.update

import java.time.LocalDate

data class AppUpdateInfo(
    val tag: String,
    val versionCode: Int,
    val versionLabel: String,
    val apkUrl: String,
    val releaseNotes: String?,
)

data class ReleaseChangelogEntry(
    val tag: String,
    val versionLabel: String,
    val notes: String,
    val publishedDay: LocalDate,
)

data class ReleaseChangelogPage(
    val entries: List<ReleaseChangelogEntry>,
    val hasMore: Boolean,
)
