package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rpsonline.app.R
import com.rpsonline.app.ui.util.formatQueueTimeMmSs

@Composable
fun TopBarSegmentedQueueIndicator(
    inMatch: Boolean,
    inQueue: Boolean,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val description = when {
        inMatch -> stringResource(
            R.string.in_match_with_time,
            formatQueueTimeMmSs(elapsedSeconds),
        )
        inQueue -> stringResource(
            R.string.in_queue_with_time,
            formatQueueTimeMmSs(elapsedSeconds),
        )
        else -> stringResource(R.string.queue_timer_idle)
    }

    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueTimeSegmentedDisplay(
            elapsedSeconds = elapsedSeconds,
            showLiveTime = inQueue || inMatch,
            animateSpinner = inQueue || inMatch,
            spinnerStyle = if (inMatch) {
                SegmentedSpinnerStyle.MATCH
            } else {
                SegmentedSpinnerStyle.QUEUE
            },
            modifier = Modifier.height(SegmentedDisplayHeight),
        )
    }
}

/** @deprecated Use [TopBarSegmentedQueueIndicator] */
@Composable
fun InQueueTopBarIndicator(
    elapsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    TopBarSegmentedQueueIndicator(
        inMatch = false,
        inQueue = true,
        elapsedSeconds = elapsedSeconds,
        modifier = modifier,
    )
}
