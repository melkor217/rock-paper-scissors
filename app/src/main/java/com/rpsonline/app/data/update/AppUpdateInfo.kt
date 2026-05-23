package com.rpsonline.app.data.update

data class AppUpdateInfo(
    val tag: String,
    val versionCode: Int,
    val versionLabel: String,
    val apkUrl: String,
    val releaseNotes: String?,
)
