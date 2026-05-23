package com.rpsonline.app

import android.app.Application

object AppCheckSetup {
    fun install(application: Application) {
        // Release builds rely on Firebase App Check enforcement defaults until Play Integrity is wired up.
    }
}
