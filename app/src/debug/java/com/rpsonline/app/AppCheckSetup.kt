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
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .addOnSuccessListener { result ->
                Log.i(
                    TAG,
                    "App Check OK for project rps-online-9771e. If auth still fails, compare the " +
                        "DebugAppCheckProvider secret in Logcat with Firebase → App Check → debug tokens.",
                )
            }
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "App Check FAILED — search Logcat for 'DebugAppCheckProvider' and copy the debug secret. " +
                        "It must be listed under Firebase → App Check → com.rpsonline.app → Manage debug tokens. " +
                        "Release APKs need Play Integrity, not a debug token.",
                    error,
                )
            }
    }

    private const val TAG = "RpsApplication"
}
