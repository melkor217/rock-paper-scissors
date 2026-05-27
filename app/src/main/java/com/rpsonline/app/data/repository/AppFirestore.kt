package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/** Production Firestore database (Belgium). */
internal const val FIRESTORE_DATABASE_ID = "europe-west1"

/** Cloud Functions region; must match `REGION` in functions/src/index.ts. */
internal const val FUNCTIONS_REGION = "europe-west1"

internal fun appFirestore(): FirebaseFirestore =
    FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DATABASE_ID)
