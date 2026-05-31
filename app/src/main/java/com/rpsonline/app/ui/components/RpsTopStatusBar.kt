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

/** Matches top-bar segmented icon slot width. */
val RpsTopBarIconSize = TopBarSegmentedIconButtonWidth

/** Inset from the left/right screen edge (display cutout horizontal inset). */
private val RpsTopBarScreenEdgePadding = 26.dp

/** Extra inset between ear content and the cutout on both sides. */
private val RpsTopBarCutoutGapPadding = 14.dp

/** Keep segmented display and icons off the top edge and rounded corners. */
private val RpsTopBarVerticalPadding = 6.dp

/** Gap between right-ear icon slots. */
private val RpsTopBarIconSpacing = 4.dp

/**
 * Global status bar split around the camera cutout: left ear | camera | right ear.
 * Left: segmented display with screen-edge padding. Right: icon block with matching padding.
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
            .padding(vertical = RpsTopBarVerticalPadding)
            .then(background)
            .rpsTopBarLayout(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = RpsTopBarScreenEdgePadding,
                    end = RpsTopBarCutoutGapPadding,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                .padding(
                    start = RpsTopBarCutoutGapPadding,
                    end = RpsTopBarScreenEdgePadding,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RpsTopBarIconSpacing),
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
