package com.rpsonline.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpsonline.app.data.model.Move

@Composable
fun RoundOutcomeCard(
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    headline: String,
    subtitle: String,
    myChoice: String?,
    opponentChoice: String?,
    choiceSeparator: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }

            if (myChoice != null && opponentChoice != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceChip(label = "You", choice = myChoice, color = contentColor)
                    Text(
                        text = choiceSeparator,
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                    )
                    ChoiceChip(label = "Opponent", choice = opponentChoice, color = contentColor)
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ChoiceChip(label: String, choice: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        Text(
            text = Move.fromString(choice)?.label ?: choice.lowercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
