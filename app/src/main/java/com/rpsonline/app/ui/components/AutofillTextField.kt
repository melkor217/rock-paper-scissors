package com.rpsonline.app.ui.components

import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.text.TextWatcher

/**
 * Native [EditText] so Android autofill / password generation works (Compose TextField often does not).
 */
@Composable
fun AutofillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    autofillHints: Array<String>,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: Int = EditorInfo.IME_ACTION_NEXT,
    onImeAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            factory = { context ->
                EditText(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setTextColor(colors.onSurface.toArgb())
                    setHintTextColor(colors.onSurfaceVariant.toArgb())
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    setAutofillHints(*autofillHints)
                    inputType = if (isPassword) {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    } else {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    }
                    imeOptions = imeAction
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(value.length)
                }
                val existing = editText.tag as? TextWatcher
                if (existing != null) {
                    editText.removeTextChangedListener(existing)
                }
                val watcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val text = s?.toString() ?: ""
                        if (text != value) onValueChange(text)
                    }
                }
                editText.tag = watcher
                editText.addTextChangedListener(watcher)
                editText.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_NEXT
                    ) {
                        onImeAction()
                        true
                    } else {
                        false
                    }
                }
            },
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = OutlinedTextFieldDefaults.UnfocusedBorderThickness,
            color = colors.outline,
        )
    }
}
