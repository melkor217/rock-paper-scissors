package com.rpsonline.app.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.rpsonline.app.ui.components.RpsCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move

@Composable
fun WaitingForOpponentCard(
    myChoice: String,
    modifier: Modifier = Modifier,
) {
    var revealed by remember(myChoice) { mutableStateOf(false) }
    val moveLabel = Move.fromString(myChoice)?.label ?: myChoice.replaceFirstChar { it.titlecase() }
    val maskedMoveText = "•".repeat(MOVE_MASK_LENGTH)

    RpsCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Your move",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (revealed) moveLabel else maskedMoveText,
                modifier = Modifier
                    .clickable(enabled = !revealed) { revealed = true }
                    .semantics {
                        contentDescription = if (revealed) {
                            "Your move: $moveLabel"
                        } else {
                            "Your move hidden"
                        }
                    },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Waiting for opponent…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val MOVE_MASK_LENGTH = Move.entries.maxOf { it.label.length }
