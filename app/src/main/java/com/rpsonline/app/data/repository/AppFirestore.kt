package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/** Production Firestore database (Finland). */
internal const val FIRESTORE_DATABASE_ID = "europe-north1"

internal fun appFirestore(): FirebaseFirestore =
    FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DATABASE_ID)
