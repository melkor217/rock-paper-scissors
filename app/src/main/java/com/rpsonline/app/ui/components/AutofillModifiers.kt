package com.rpsonline.app.ui.components

import android.os.Build
import android.view.View
import android.view.autofill.AutofillManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

/**
 * Applies Android autofill hints to the focused text field.
 * Registration should use [autofillUsername] + [autofillNewPassword] so password managers
 * offer "Suggest strong password".
 */
private fun Modifier.autofillHints(vararg hints: String): Modifier = composed {
    val rootView = LocalView.current
    onFocusChanged { state ->
        if (!state.isFocused) return@onFocusChanged
        val target = rootView.findFocus() ?: return@onFocusChanged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ViewCompat.setImportantForAutofill(target, View.IMPORTANT_FOR_AUTOFILL_YES)
            ViewCompat.setAutofillHints(target, *hints)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                rootView.context.getSystemService(AutofillManager::class.java)
                    ?.notifyValueChanged(target)
            }
        }
    }
}

fun Modifier.autofillEmailAddress(): Modifier =
    autofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)

fun Modifier.autofillUsername(): Modifier =
    autofillHints(View.AUTOFILL_HINT_USERNAME)

fun Modifier.autofillPassword(): Modifier =
    autofillHints(View.AUTOFILL_HINT_PASSWORD)

fun Modifier.autofillNewPassword(): Modifier =
    autofillHints("newPassword")

fun Modifier.excludeFromAutofill(): Modifier = composed {
    val rootView = LocalView.current
    onFocusChanged { state ->
        if (!state.isFocused) return@onFocusChanged
        val target = rootView.findFocus() ?: return@onFocusChanged
        ViewCompat.setImportantForAutofill(
            target,
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
        )
    }
}
