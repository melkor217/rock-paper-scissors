package com.rpsonline.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.getIntField(name: String): Int? =
    (get(name) as? Number)?.toInt()
