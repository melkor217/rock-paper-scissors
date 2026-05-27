package com.rpsonline.app.domain

/** Number of active bars (1–4) for a Firestore round-trip latency. */
fun firebasePingBarCount(latencyMs: Int): Int = when {
    latencyMs < 120 -> 4
    latencyMs < 250 -> 3
    latencyMs < 500 -> 2
    else -> 1
}
