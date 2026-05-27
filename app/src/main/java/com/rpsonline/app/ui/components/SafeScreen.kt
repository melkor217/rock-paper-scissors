package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full-screen content below the global top overlay; respects cutouts and gesture nav. */
@Composable
fun Modifier.rpsScreenPadding(): Modifier =
    fillMaxSize()
        .windowInsetsPadding(
            WindowInsets.displayCutout.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
        )
        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 44.dp)
