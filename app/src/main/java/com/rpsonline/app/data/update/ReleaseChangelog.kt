package com.rpsonline.app.data.update

import org.json.JSONArray
import org.json.JSONObject

object ReleaseChangelog {
    fun tagForInstalledVersion(versionName: String): String =
        if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"

    fun buildDisplayText(releaseBody: String?, compareSection: String?): String? {
        val summary = releaseBody?.trim()?.takeIf { it.isNotBlank() }?.let(::simplifyMarkdownForDisplay)
        val commits = compareSection?.trim()?.takeIf { it.isNotBlank() }
        return when {
            summary != null && commits != null -> "$summary\n\n$commits"
            summary != null -> summary
            commits != null -> commits
            else -> null
        }
    }

    data class CompareCommitLine(val message: String, val author: String?)

    fun formatCompareCommitLines(lines: List<CompareCommitLine>): String {
        if (lines.isEmpty()) return ""
        return buildList {
            add("All changes since your version:")
            lines.forEach { entry ->
                val message = entry.message.lineSequence().firstOrNull()?.trim().orEmpty()
                if (message.isBlank()) return@forEach
                add(buildString {
                    append("• ")
                    append(message)
                    entry.author?.takeIf { it.isNotBlank() }?.let { name ->
                        append(" — ")
                        append(name)
                    }
                })
            }
        }.joinToString("\n")
    }

    fun formatCompareCommits(commits: JSONArray): String {
        if (commits.length() == 0) return ""
        val lines = buildList {
            for (i in 0 until commits.length()) {
                val entry = commits.optJSONObject(i) ?: continue
                val commit = entry.optJSONObject("commit") ?: continue
                val message = commit.optString("message")
                val author = commit.optJSONObject("author")?.optString("name")
                    ?: commit.optJSONObject("committer")?.optString("name")
                add(CompareCommitLine(message, author))
            }
        }
        return formatCompareCommitLines(lines)
    }

    fun simplifyMarkdownForDisplay(markdown: String): String {
        var text = markdown
        text = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(text) { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            if (label.equals(url, ignoreCase = true)) url else "$label ($url)"
        }
        text = text.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        text = text.replace("**", "")
        return text.trim()
    }

    fun parseCompareRangeFromReleaseBody(body: String): Pair<String, String>? {
        val match = Regex("""compare/([^/\s]+)\.\.\.([^\s)]+)""").find(body) ?: return null
        val base = match.groupValues[1]
        val head = match.groupValues[2]
        if (base.isBlank() || head.isBlank()) return null
        return base to head
    }
}
