package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top bar row that draws into the display cutout band.
 * Icons sit at the top beside the camera — no extra height below the cutout.
 */
@Composable
fun Modifier.rpsTopBarLayout(): Modifier =
    fillMaxWidth()
        .windowInsetsPadding(
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
        )

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
