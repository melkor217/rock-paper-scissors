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
    fun fetchLatestRelease(): AppUpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/$owner/$repo/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RpsOnline-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(body)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseRelease(json: String): AppUpdateInfo? {
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

            val notes = root.optString("body").takeIf { it.isNotBlank() }
            AppUpdateInfo(
                tag = tag,
                versionCode = versionCodeFromTag(tag),
                versionLabel = versionLabelFromTag(tag),
                apkUrl = url,
                releaseNotes = notes,
            )
        } catch (_: Exception) {
            null
        }
    }
}
