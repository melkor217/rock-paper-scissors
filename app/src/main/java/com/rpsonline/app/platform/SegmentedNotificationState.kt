package com.rpsonline.app.platform

/** Latest online count for segmented notification rendering. */
object SegmentedNotificationState {
    @Volatile
    var onlineCount: Int? = null
        private set

    fun setOnlineCount(count: Int?) {
        onlineCount = count
    }
}
