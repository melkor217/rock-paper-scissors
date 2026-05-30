package com.rpsonline.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundBannerCopyTest {

    @Test
    fun headlines_includeRoundNumber() {
        assertEquals("Won round 3", roundBannerHeadline(RoundBannerKind.Win, 3))
        assertEquals("Lost round 3", roundBannerHeadline(RoundBannerKind.Lose, 3))
        assertEquals("Draw · R3", roundBannerHeadline(RoundBannerKind.Draw, 3))
    }

    @Test
    fun subtitles_winLose_unified() {
        assertEquals("+1 point", roundBannerSubtitle(RoundBannerKind.Win, compact = false, showFollowUpHint = false))
        assertEquals(
            "+1 point · pick next",
            roundBannerSubtitle(RoundBannerKind.Win, compact = false, showFollowUpHint = true),
        )
        assertEquals("", roundBannerSubtitle(RoundBannerKind.Lose, compact = true, showFollowUpHint = true))
        assertEquals("They scored", roundBannerSubtitle(RoundBannerKind.Lose, compact = false, showFollowUpHint = false))
    }

    @Test
    fun subtitles_draw() {
        assertEquals("No point.", roundBannerSubtitle(RoundBannerKind.Draw, compact = false, showFollowUpHint = false))
        assertEquals(
            "Replay · score unchanged.",
            roundBannerSubtitle(RoundBannerKind.Draw, compact = false, showFollowUpHint = true),
        )
    }
}
