package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Top bar container that extends into the display cutout region.
 * Icons sit in the side "ears" beside the camera — not pushed below the cutout.
 */
@Composable
fun Modifier.rpsTopBarLayout(): Modifier {
    val density = LocalDensity.current
    val cutoutTopPx = WindowInsets.statusBars
        .union(WindowInsets.displayCutout)
        .getTop(density)
    val minBarHeight = with(density) { cutoutTopPx.toDp() }.coerceAtLeast(20.dp) + 36.dp
    return this
        .fillMaxWidth()
        .windowInsetsPadding(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
        )
        .heightIn(min = minBarHeight)
}

/** Screen content below the global top bar; respects side cutouts and gesture nav. */
@Composable
fun Modifier.rpsScreenPadding(): Modifier =
    fillMaxSize()
        .windowInsetsPadding(
            WindowInsets.displayCutout.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
        )
        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 12.dp)
