package com.rpsonline.app.data.repository

import android.app.Activity
import android.content.Context
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.data.update.AppUpdateInfo
import com.rpsonline.app.data.update.AppUpdateInstaller
import com.rpsonline.app.data.update.GitHubReleaseClient
import java.io.File

sealed class UpdateCheckOutcome {
    data class UpdateAvailable(val update: AppUpdateInfo) : UpdateCheckOutcome()
    data object UpToDate : UpdateCheckOutcome()
    data object CheckFailed : UpdateCheckOutcome()
}

class AppUpdateRepository(
    private val context: Context,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(
        owner = BuildConfig.GITHUB_REPO_OWNER,
        repo = BuildConfig.GITHUB_REPO_NAME,
    ),
    private val installer: AppUpdateInstaller = AppUpdateInstaller(
        authority = "${context.packageName}.fileprovider",
    ),
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun updatesEnabled(): Boolean = BuildConfig.GITHUB_UPDATES_ENABLED

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    fun fetchInstalledReleaseNotes(): String? =
        releaseClient.fetchReleaseNotesForVersion(currentVersionName())

    fun shouldSkipAutoUpdateCheck(): Boolean {
        val lastCheckMs = prefs.getLong(KEY_LAST_AUTO_CHECK_NO_UPDATE_MS, 0L)
        if (lastCheckMs == 0L) return false
        return System.currentTimeMillis() - lastCheckMs < AUTO_CHECK_COOLDOWN_MS
    }

    fun recordAutoCheckWithNoUpdate() {
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK_NO_UPDATE_MS, System.currentTimeMillis()).apply()
    }

    fun checkForUpdate(): UpdateCheckOutcome {
        if (!updatesEnabled()) return UpdateCheckOutcome.UpToDate
        val latest = releaseClient.fetchLatestRelease(currentVersionName())
            ?: return UpdateCheckOutcome.CheckFailed
        return if (latest.versionCode > currentVersionCode()) {
            UpdateCheckOutcome.UpdateAvailable(latest)
        } else {
            UpdateCheckOutcome.UpToDate
        }
    }

    suspend fun downloadAndInstall(
        activity: Activity,
        update: AppUpdateInfo,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ) {
        if (!installer.canInstallPackages(activity)) {
            installer.openInstallPermissionSettings(activity)
            error("Allow installs from this app in Settings, then try again.")
        }

        val apkFile = File(context.cacheDir, "updates/rps-online-update.apk")
        installer.downloadApk(update.apkUrl, apkFile, onProgress)
        installer.launchInstall(activity, apkFile)
    }

    companion object {
        private const val PREFS_NAME = "app_update"
        private const val KEY_LAST_AUTO_CHECK_NO_UPDATE_MS = "last_auto_check_no_update_ms"
        private const val AUTO_CHECK_COOLDOWN_MS = 60 * 60 * 1000L
    }
}
