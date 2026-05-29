package com.rpsonline.app.data.repository

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until the task completes. Unlike [kotlinx.coroutines.tasks.await], cancellation
 * resumes immediately so [kotlinx.coroutines.withTimeout] can recover the UI.
 *
 * Firestore writes ([com.google.firebase.firestore.DocumentReference.set], etc.) complete with
 * a null [Task.getResult] on success — that is expected, not an error.
 */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
  if (isComplete) {
    if (isSuccessful) {
      @Suppress("UNCHECKED_CAST")
      cont.resume(result as T)
    } else {
      cont.resumeWithException(exception ?: IllegalStateException("Task failed"))
    }
    return@suspendCancellableCoroutine
  }
  addOnCompleteListener { task ->
    if (cont.isCancelled) return@addOnCompleteListener
    if (task.isSuccessful) {
      @Suppress("UNCHECKED_CAST")
      cont.resume(task.result as T)
    } else {
      cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
  }
}
