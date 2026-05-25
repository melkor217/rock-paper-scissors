package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.UserProfile
import com.rpsonline.app.ui.components.PlayerStatsWidget
import java.util.Locale

@Composable
fun PreGameCountdownScreen(
    secondsRemaining: Double,
    myDisplayName: String,
    opponentDisplayName: String,
    myProfile: UserProfile?,
    opponentProfile: UserProfile?,
    myElo: Int?,
    opponentElo: Int?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Match starting in",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = String.format(Locale.US, "%.1f", secondsRemaining.coerceAtLeast(0.0)),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayerStatsWidget(
                displayName = myDisplayName,
                profile = myProfile,
                eloOverride = myElo,
            )
            PlayerStatsWidget(
                displayName = opponentDisplayName,
                profile = opponentProfile,
                eloOverride = opponentElo,
            )
        }
        Button(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(
                text = "To battle!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
