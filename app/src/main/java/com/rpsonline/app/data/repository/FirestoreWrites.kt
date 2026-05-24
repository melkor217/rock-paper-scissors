package com.rpsonline.app.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference

/** Attach a no-op failure handler so rejected writes cannot crash the main thread. */
internal fun <T> Task<T>.ignoreWriteFailures(): Task<T> =
    addOnFailureListener { }

internal fun DocumentReference.setBestEffort(data: Map<String, Any>) {
    set(data).ignoreWriteFailures()
}

internal fun DocumentReference.updateBestEffort(data: Map<String, Any>) {
    update(data).ignoreWriteFailures()
}

internal fun DocumentReference.deleteBestEffort() {
    delete().ignoreWriteFailures()
}
