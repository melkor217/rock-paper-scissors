package com.rpsonline.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

internal fun appFirestore(): FirebaseFirestore =
    FirebaseFirestore.getInstance(FirebaseApp.getInstance())
