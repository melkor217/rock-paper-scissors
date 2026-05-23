package com.rpsonline.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/** Maps fields to Android Autofill (e.g. Google Password Manager suggest / save). */
fun Modifier.autofillEmail(): Modifier =
    semantics { contentType = ContentType.EmailAddress }

fun Modifier.autofillPassword(): Modifier =
    semantics { contentType = ContentType.Password }

fun Modifier.autofillNewPassword(): Modifier =
    semantics { contentType = ContentType.NewPassword }

fun Modifier.autofillPersonName(): Modifier =
    semantics { contentType = ContentType.PersonName }
