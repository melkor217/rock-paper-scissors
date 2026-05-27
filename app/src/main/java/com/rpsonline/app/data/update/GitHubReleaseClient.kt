package com.rpsonline.app.data.update

import com.rpsonline.app.domain.versionCodeFromTag
import com.rpsonline.app.domain.versionLabelFromTag
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseClient(
    private val owner: String,
    private val repo: String,
) {
    fun fetchReleaseNotesForVersion(versionName: String): String? {
        val tag = ReleaseChangelog.tagForInstalledVersion(versionName)
        var connection: HttpURLConnection? = null
        return try {
            connection = openGetConnection("https://api.github.com/repos/$owner/$repo/releases/tags/$tag")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseBody = JSONObject(body).optString("body").takeIf { it.isNotBlank() }
                ?: return null
            ReleaseChangelog.simplifyMarkdownForDisplay(releaseBody)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun fetchReleasesPage(page: Int, perPage: Int = RELEASES_PAGE_SIZE): ReleaseChangelogPage {
        var connection: HttpURLConnection? = null
        return try {
            connection = openGetConnection(
                "https://api.github.com/repos/$owner/$repo/releases?page=$page&per_page=$perPage",
            )
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return ReleaseChangelogPage(entries = emptyList(), hasMore = false)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val entries = parseReleaseList(body)
            ReleaseChangelogPage(
                entries = entries,
                hasMore = entries.size >= perPage,
            )
        } catch (_: Exception) {
            ReleaseChangelogPage(entries = emptyList(), hasMore = false)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseReleaseList(json: String): List<ReleaseChangelogEntry> {
        val releases = JSONArray(json)
        return buildList {
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name").takeIf { it.isNotBlank() } ?: continue
                val rawBody = release.optString("body")
                val notes = rawBody.takeIf { it.isNotBlank() }
                    ?.let(ReleaseChangelog::simplifyMarkdownForDisplay)
                    ?: "No release notes."
                add(
                    ReleaseChangelogEntry(
                        tag = tag,
                        versionLabel = versionLabelFromTag(tag),
                        notes = notes,
                    ),
                )
            }
        }
    }

    private fun openGetConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RpsOnline-Android")
        }

    fun fetchLatestRelease(installedVersionName: String): AppUpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = openGetConnection("https://api.github.com/repos/$owner/$repo/releases/latest")
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
            connection = openGetConnection(
                "https://api.github.com/repos/$owner/$repo/compare/$compareRef",
            )
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

    companion object {
        const val RELEASES_PAGE_SIZE = 10
    }
}
