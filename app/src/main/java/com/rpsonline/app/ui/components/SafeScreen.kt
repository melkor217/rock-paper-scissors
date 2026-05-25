package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Edge-to-edge safe area; extra top inset for the global appearance button. */
@Composable
fun Modifier.rpsScreenPadding(): Modifier =
    fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 52.dp)
