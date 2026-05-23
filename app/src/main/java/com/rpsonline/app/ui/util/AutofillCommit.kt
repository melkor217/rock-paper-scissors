package com.rpsonline.app.ui.util

import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager

/**
 * Prompt the autofill service to save credentials after successful registration.
 * Call while email/password fields still hold the values the user entered.
 */
fun commitAutofillSave(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
    val autofillManager = context.getSystemService(AutofillManager::class.java) ?: return
    if (!autofillManager.isAutofillSupported || !autofillManager.isEnabled) return
    autofillManager.commit()
}
