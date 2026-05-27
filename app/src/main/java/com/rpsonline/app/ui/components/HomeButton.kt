package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String = "Home",
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
    }
}
