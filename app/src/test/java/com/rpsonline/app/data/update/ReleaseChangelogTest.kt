package com.rpsonline.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseChangelogTest {

    @Test
    fun tagForInstalledVersion_addsVPrefix() {
        assertEquals("v0.6.3", ReleaseChangelog.tagForInstalledVersion("0.6.3"))
        assertEquals("v1.0.0", ReleaseChangelog.tagForInstalledVersion("v1.0.0"))
    }

    @Test
    fun parseCompareRangeFromReleaseBody_extractsTags() {
        val body = "**Full Changelog**: https://github.com/o/r/compare/v0.6.3...v0.6.4"
        assertEquals("v0.6.3" to "v0.6.4", ReleaseChangelog.parseCompareRangeFromReleaseBody(body))
    }

    @Test
    fun formatCompareCommitLines_listsMessagesWithoutAuthors() {
        val text = ReleaseChangelog.formatCompareCommitLines(
            listOf(
                ReleaseChangelog.CompareCommitLine(
                    message = "Add BO3 support\n\nDetails",
                    author = "Dan",
                ),
            ),
        )
        assertTrue(text.contains("Add BO3 support"))
        assertFalse(text.contains("Dan"))
    }

    @Test
    fun buildDisplayText_combinesSummaryAndCommits() {
        val result = ReleaseChangelog.buildDisplayText(
            releaseBody = "## What's Changed\n* Feature",
            compareSection = "All changes since your version:\n• Fix bug",
        )
        assertNotNull(result)
        assertFalse(result!!.contains("What's Changed"))
        assertTrue(result.contains("Feature"))
        assertTrue(result.contains("Fix bug"))
    }

    @Test
    fun simplifyMarkdownForDisplay_stripsNoiseFromReleaseNotes() {
        val raw = """
            ## What's Changed
            * Add match sounds by @dan in https://github.com/o/r/pull/46
            * Fix queue timer

            **Full Changelog**: https://github.com/o/r/compare/v0.6.3...v0.6.4
        """.trimIndent()

        val result = ReleaseChangelog.simplifyMarkdownForDisplay(raw)

        assertFalse(result.contains("What's Changed"))
        assertFalse(result.contains("Full Changelog"))
        assertFalse(result.contains("pull/46"))
        assertFalse(result.contains("@dan"))
        assertFalse(result.contains(" in "))
        assertTrue(result.contains("Add match sounds"))
        assertTrue(result.contains("Fix queue timer"))
    }

    @Test
    fun simplifyMarkdownForDisplay_stripsOrphanInAfterMarkdownPullLink() {
        val raw = "* Add match sounds by @dan in [PR #46](https://github.com/o/r/pull/46)"
        val result = ReleaseChangelog.simplifyMarkdownForDisplay(raw)
        assertEquals("Add match sounds", result)
    }

    @Test
    fun simplifyMarkdownForDisplay_stripsOrphanInAfterPlainPullUrl() {
        val raw = """
            ## What's Changed
            * Add changelog screen from version footer by @melkor217 in https://github.com/melkor217/rock-paper-scissors/pull/50
            * Add Firebase ping meter next to theme selector by @melkor217 in https://github.com/melkor217/rock-paper-scissors/pull/49
        """.trimIndent()

        val result = ReleaseChangelog.simplifyMarkdownForDisplay(raw)

        assertEquals(
            "Add changelog screen from version footer\nAdd Firebase ping meter next to theme selector",
            result,
        )
        assertFalse(result.contains(" in"))
    }

    @Test
    fun simplifyMarkdownForDisplay_stripsNewContributorsHeading() {
        val raw = """
            ## What's Changed
            * Fix bug

            ## New Contributors
            * @newbie made their first contribution
        """.trimIndent()

        val result = ReleaseChangelog.simplifyMarkdownForDisplay(raw)

        assertFalse(result.contains("New Contributors", ignoreCase = true))
        assertTrue(result.contains("Fix bug"))
    }

    @Test
    fun notesToListItems_splitsLinesAndStripsBullets() {
        assertEquals(
            listOf("Add sounds", "Fix timer"),
            ReleaseChangelog.notesToListItems("Add sounds\n• Fix timer"),
        )
    }

    @Test
    fun mergeEntriesByDay_combinesNotesUnderNewestVersion() {
        val day = LocalDate.of(2026, 5, 27)
        val merged = ReleaseChangelog.mergeEntriesByDay(
            listOf(
                ReleaseChangelogEntry("v0.6.5", "0.6.5", "New feature", day),
                ReleaseChangelogEntry("v0.6.4", "0.6.4", "Hotfix", day),
                ReleaseChangelogEntry("v0.6.3", "0.6.3", "Older day", day.minusDays(1)),
            ),
        )

        assertEquals(2, merged.size)
        assertEquals("0.6.5", merged[0].versionLabel)
        assertEquals("New feature\nHotfix", merged[0].notes)
        assertEquals("0.6.3", merged[1].versionLabel)
    }
}
