package com.rpsonline.app.data.repository

import com.rpsonline.app.data.model.UserProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide profile sync lock. [AuthRepository] is constructed in multiple ViewModels;
 * a per-instance mutex did not serialize sign-in vs home refresh vs matchmaking.
 */
internal object UserProfileSync {
    private val mutex = Mutex()

    @Volatile
    var lastQueueReady: Pair<String, UserProfile>? = null
        private set

    suspend fun <T> withProfileLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun rememberQueueReady(uid: String, profile: UserProfile) {
        lastQueueReady = uid to profile
    }

    fun queueReadyProfile(uid: String): UserProfile? =
        lastQueueReady?.takeIf { it.first == uid }?.second

    fun clear() {
        lastQueueReady = null
    }
}
