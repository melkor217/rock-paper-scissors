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
                "GitHub release APK"
            }
            Log.i(
                TAG,
                "App Check ($label): open Logcat, filter DebugAppCheckProvider, register the debug " +
                    "secret in Firebase → App Check → com.rpsonline.app → Manage debug tokens, " +
                    "then cold-restart the app.",
            )
            logTokenProbe()
        } else {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            Log.i(TAG, "App Check: Play Integrity (Play Store / production release)")
        }
    }

    private fun logTokenProbe() {
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .addOnSuccessListener {
                Log.i(TAG, "App Check token obtained successfully.")
            }
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "App Check token failed — register the DebugAppCheckProvider secret in Firebase.",
                    error,
                )
            }
    }

    private const val TAG = "RpsApplication"
}
