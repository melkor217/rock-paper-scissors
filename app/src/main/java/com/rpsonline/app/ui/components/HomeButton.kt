package com.rpsonline.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.rpsonline.app.R

@Composable
fun HomeOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String = stringResource(R.string.back_to_home),
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
    }
}
