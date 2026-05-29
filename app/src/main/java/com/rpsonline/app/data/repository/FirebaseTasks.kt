package com.rpsonline.app.data.repository

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until the task completes. Unlike [kotlinx.coroutines.tasks.await], cancellation
 * resumes immediately so [kotlinx.coroutines.withTimeout] can recover the UI.
 */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
  if (isComplete) {
    if (isSuccessful) {
      val value = result
      if (value != null) {
        @Suppress("UNCHECKED_CAST")
        cont.resume(value as T)
      } else {
        cont.resumeWithException(IllegalStateException("Task succeeded with null result"))
      }
    } else {
      cont.resumeWithException(exception ?: IllegalStateException("Task failed"))
    }
    return@suspendCancellableCoroutine
  }
  addOnCompleteListener { task ->
    if (cont.isCancelled) return@addOnCompleteListener
    if (task.isSuccessful) {
      val value = task.result
      if (value != null) {
        @Suppress("UNCHECKED_CAST")
        cont.resume(value as T)
      } else {
        cont.resumeWithException(IllegalStateException("Task succeeded with null result"))
      }
    } else {
      cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
  }
}
