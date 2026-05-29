package com.rpsonline.app

import android.app.Application
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

object AppCheckSetup {
    fun install(application: Application) {
        val useDebugProvider = BuildConfig.DEBUG || BuildConfig.USE_DEBUG_APP_CHECK
        if (useDebugProvider) {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            val label = if (BuildConfig.DEBUG) {
                "debug build"
            } else {
                "release APK (GitHub or Studio)"
            }
            Log.i(
                TAG,
                "App Check ($label): filter Logcat for DebugAppCheckProvider, copy the debug secret, " +
                    "add it in Firebase → App Check → com.rpsonline.app → Manage debug tokens, " +
                    "then force-stop and reopen the app. Each signing key needs its own token.",
            )
        } else {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            Log.i(TAG, "App Check: Play Integrity (Play Store / production release)")
        }
    }

    private const val TAG = "RpsApplication"
}
