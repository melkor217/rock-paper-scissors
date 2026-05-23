package com.rpsonline.app.data.repository

import android.app.Activity
import android.content.Context
import com.rpsonline.app.BuildConfig
import com.rpsonline.app.data.update.AppUpdateInfo
import com.rpsonline.app.data.update.AppUpdateInstaller
import com.rpsonline.app.data.update.GitHubReleaseClient
import java.io.File

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
    fun updatesEnabled(): Boolean = BuildConfig.GITHUB_UPDATES_ENABLED

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    fun fetchUpdateIfAvailable(): AppUpdateInfo? {
        if (!updatesEnabled()) return null
        val latest = releaseClient.fetchLatestRelease() ?: return null
        return latest.takeIf { it.versionCode > currentVersionCode() }
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
}
