package com.rpsonline.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun formatCompareCommitLines_listsMessages() {
        val text = ReleaseChangelog.formatCompareCommitLines(
            listOf(
                ReleaseChangelog.CompareCommitLine(
                    message = "Add BO3 support\n\nDetails",
                    author = "Dan",
                ),
            ),
        )
        assertTrue(text.contains("Add BO3 support"))
        assertTrue(text.contains("Dan"))
    }

    @Test
    fun buildDisplayText_combinesSummaryAndCommits() {
        val result = ReleaseChangelog.buildDisplayText(
            releaseBody = "## What's Changed\n* Feature",
            compareSection = "All changes since your version:\n• Fix bug",
        )
        assertNotNull(result)
        assertTrue(result!!.contains("What's Changed"))
        assertTrue(result.contains("Fix bug"))
    }

    @Test
    fun simplifyMarkdownForDisplay_expandsLinks() {
        val raw = "See [PR #46](https://github.com/o/r/pull/46) for details."
        assertEquals(
            "See PR #46 (https://github.com/o/r/pull/46) for details.",
            ReleaseChangelog.simplifyMarkdownForDisplay(raw),
        )
    }
}
