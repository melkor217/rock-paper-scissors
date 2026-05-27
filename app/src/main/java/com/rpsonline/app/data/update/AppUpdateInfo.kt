package com.rpsonline.app.data.update

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
)

data class ReleaseChangelogPage(
    val entries: List<ReleaseChangelogEntry>,
    val hasMore: Boolean,
)
