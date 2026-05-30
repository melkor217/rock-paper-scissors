package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat

/** Matches top-bar icon size (see [ClockSoundMuteButton]). */
val RpsTopBarIconSize = 24.dp

/** Left ear inset; nudges segmented displays slightly in from the screen edge. */
private val RpsTopBarEarStartPadding = 22.dp

/**
 * Global status bar split around the camera cutout: left ear | camera | right ear.
 * Each ear uses [RpsTopBarIconSize] horizontal padding inside its box.
 */
@Composable
fun RpsTopStatusBar(
    modifier: Modifier = Modifier,
    background: Modifier = Modifier,
    leftContent: @Composable RowScope.() -> Unit,
    rightContent: @Composable RowScope.() -> Unit,
) {
    val cutoutGap = rememberTopDisplayCutoutGap()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(background)
            .rpsTopBarLayout(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = RpsTopBarEarStartPadding, end = RpsTopBarIconSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = leftContent,
            )
        }
        if (cutoutGap > 0.dp) {
            Spacer(modifier = Modifier.width(cutoutGap))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = RpsTopBarIconSize),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                content = rightContent,
            )
        }
    }
}

@Composable
private fun rememberTopDisplayCutoutGap(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var gap by remember { mutableStateOf(0.dp) }

    DisposableEffect(view, density) {
        fun readGap(): Dp {
            val cutout = ViewCompat.getRootWindowInsets(view)?.displayCutout ?: return 0.dp
            val gapPx = cutout.topCenterGapWidthPx()
            return with(density) { gapPx.toDp() }
        }

        fun update() {
            gap = readGap()
        }

        update()
        val listener = android.view.View.OnApplyWindowInsetsListener { _, insets ->
            update()
            insets
        }
        view.setOnApplyWindowInsetsListener(listener)
        onDispose {
            view.setOnApplyWindowInsetsListener(null)
        }
    }

    return gap
}

/** Width of the top-center camera cutout, or 0 when none. */
private fun DisplayCutoutCompat.topCenterGapWidthPx(): Int {
    if (boundingRects.isEmpty()) return 0
    return boundingRects
        .filter { rect -> rect.top <= safeInsetTop }
        .maxOfOrNull { it.width() }
        ?: boundingRects.maxOf { it.width() }
}
