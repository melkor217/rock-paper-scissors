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
                add("• $message")
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

    fun simplifyMarkdownForDisplay(markdown: String): String =
        markdown.lines()
            .mapNotNull(::cleanChangelogLine)
            .joinToString("\n")
            .trim()

    fun parseCompareRangeFromReleaseBody(body: String): Pair<String, String>? {
        val match = Regex("""compare/([^/\s]+)\.\.\.([^\s)]+)""").find(body) ?: return null
        val base = match.groupValues[1]
        val head = match.groupValues[2]
        if (base.isBlank() || head.isBlank()) return null
        return base to head
    }

    private fun cleanChangelogLine(raw: String): String? {
        var line = raw.trim()
        if (line.isBlank()) return null

        line = line.replace(Regex("^#{1,6}\\s+"), "")
        line = line.replace("**", "").trim()
        if (line.isBlank()) return null

        if (FULL_CHANGELOG_LINE.matches(line)) return null
        if (WHATS_HEADING.matches(line)) return null

        line = stripMarkdownLinks(line)
        line = stripAuthorAttribution(line)
        line = line.replace("**", "").trim()
        line = line.removePrefix("*").removePrefix("-").trim()
        line = line.removePrefix("•").trim()

        if (line.isBlank() || FULL_CHANGELOG_LINE.matches(line) || WHATS_HEADING.matches(line)) {
            return null
        }
        return line
    }

    private fun stripMarkdownLinks(text: String): String =
        IN_MARKDOWN_PULL_REQUEST.replace(text, "")
            .replace(IN_PULL_REQUEST, "")
            .let { MARKDOWN_LINK.replace(it) { match ->
                val label = match.groupValues[1].trim()
                val url = match.groupValues[2].trim()
                when {
                    url.contains("/pull/", ignoreCase = true) -> ""
                    url.contains("/compare/", ignoreCase = true) -> ""
                    label.equals(url, ignoreCase = true) -> ""
                    else -> label
                }
            } }
            .replace(PULL_REQUEST_URL, "")
            .replace(COMPARE_URL, "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    private fun stripAuthorAttribution(text: String): String =
        text.replace(BY_AUTHOR, "")
            .replace(IN_PULL_REQUEST, "")
            .replace(ORPHAN_IN, "")
            .trim()

    private val MARKDOWN_LINK = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    private val FULL_CHANGELOG_LINE =
        Regex("""(?i)^\*{0,2}full changelog\*{0,2}\s*:.*$""")
    private val WHATS_HEADING =
        Regex("""(?i)^what'?s (changed|added|new)\s*:?\s*$""")
    private val BY_AUTHOR = Regex("""\s+by\s+@[\w-]+""", RegexOption.IGNORE_CASE)
    private val IN_PULL_REQUEST =
        Regex("""\s+in\s+https://github\.com/[^\s]+/pull/\d+""", RegexOption.IGNORE_CASE)
    private val IN_MARKDOWN_PULL_REQUEST =
        Regex("""\s+in\s+\[[^\]]+]\([^)]*/pull/\d+[^)]*\)""", RegexOption.IGNORE_CASE)
    private val ORPHAN_IN = Regex("""\s+in\s*$""", RegexOption.IGNORE_CASE)
    private val PULL_REQUEST_URL =
        Regex("""https://github\.com/[^\s]+/pull/\d+""", RegexOption.IGNORE_CASE)
    private val COMPARE_URL =
        Regex("""https://github\.com/[^\s]+/compare/[^\s)]+""", RegexOption.IGNORE_CASE)
}
