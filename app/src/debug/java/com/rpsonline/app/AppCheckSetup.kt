package com.rpsonline.app

import android.app.Application
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

object AppCheckSetup {
    fun install(application: Application) {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        Log.i(
            TAG,
            "App Check DEBUG: In Logcat, filter 'DebugAppCheckProvider' and register the " +
                "debug secret in Firebase → App Check → your Android app → Manage debug tokens",
        )
    }

    private const val TAG = "RpsApplication"
}
