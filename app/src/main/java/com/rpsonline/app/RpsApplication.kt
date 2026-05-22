package com.rpsonline.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class RpsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        if (BuildConfig.DEBUG) {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            Log.i(
                TAG,
                "App Check DEBUG: In Logcat, filter 'DebugAppCheckProvider' and register the " +
                    "debug secret in Firebase → App Check → your Android app → Manage debug tokens",
            )
        }
    }

    companion object {
        private const val TAG = "RpsApplication"
    }
}
