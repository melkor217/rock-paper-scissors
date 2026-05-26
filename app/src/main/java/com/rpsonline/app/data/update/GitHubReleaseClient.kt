package com.rpsonline.app.data.update

import com.rpsonline.app.domain.versionCodeFromTag
import com.rpsonline.app.domain.versionLabelFromTag
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(
    private val owner: String,
    private val repo: String,
) {
    fun fetchLatestRelease(installedVersionName: String): AppUpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/$owner/$repo/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RpsOnline-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(body, installedVersionName)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseRelease(json: String, installedVersionName: String): AppUpdateInfo? {
        return try {
            val root = JSONObject(json)
            val tag = root.optString("tag_name")
            if (tag.isBlank()) return null

            val assets = root.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            val url = apkUrl?.takeIf { it.isNotBlank() } ?: return null

            val releaseBody = root.optString("body").takeIf { it.isNotBlank() }
            val (baseTag, headTag) = releaseBody?.let { ReleaseChangelog.parseCompareRangeFromReleaseBody(it) }
                ?: (ReleaseChangelog.tagForInstalledVersion(installedVersionName) to tag)
            val compareSection = if (baseTag != headTag) {
                fetchCompareChangelog(baseTag, headTag)
            } else {
                null
            }
            val changelog = ReleaseChangelog.buildDisplayText(releaseBody, compareSection)

            AppUpdateInfo(
                tag = tag,
                versionCode = versionCodeFromTag(tag),
                versionLabel = versionLabelFromTag(tag),
                apkUrl = url,
                releaseNotes = changelog,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchCompareChangelog(baseTag: String, headTag: String): String? {
        if (baseTag.isBlank() || headTag.isBlank() || baseTag == headTag) return null
        var connection: HttpURLConnection? = null
        return try {
            val compareRef = "$baseTag...$headTag"
            connection = (URL("https://api.github.com/repos/$owner/$repo/compare/$compareRef")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RpsOnline-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val commits = JSONObject(body).optJSONArray("commits") ?: return null
            ReleaseChangelog.formatCompareCommits(commits).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
