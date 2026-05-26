package com.rpsonline.app.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundBannerCopyTest {

    @Test
    fun headlines_includeRoundNumber() {
        assertEquals("You won Round #3", roundBannerHeadline(RoundBannerKind.Win, 3))
        assertEquals("You lost Round #3", roundBannerHeadline(RoundBannerKind.Lose, 3))
        assertEquals("Draw on Round #3", roundBannerHeadline(RoundBannerKind.Draw, 3))
    }

    @Test
    fun subtitles_winLose_unified() {
        assertEquals("Point scored.", roundBannerSubtitle(RoundBannerKind.Win, compact = false, showFollowUpHint = false))
        assertEquals(
            "Point scored — pick your move for the next round.",
            roundBannerSubtitle(RoundBannerKind.Win, compact = false, showFollowUpHint = true),
        )
        assertEquals("", roundBannerSubtitle(RoundBannerKind.Lose, compact = true, showFollowUpHint = true))
        assertEquals("Opponent scored.", roundBannerSubtitle(RoundBannerKind.Lose, compact = false, showFollowUpHint = false))
    }

    @Test
    fun subtitles_draw() {
        assertEquals("No point awarded.", roundBannerSubtitle(RoundBannerKind.Draw, compact = false, showFollowUpHint = false))
        assertEquals(
            "Replay this round. Score unchanged.",
            roundBannerSubtitle(RoundBannerKind.Draw, compact = false, showFollowUpHint = true),
        )
    }
}
