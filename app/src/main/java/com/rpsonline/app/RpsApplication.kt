package com.rpsonline.app

import android.app.Application
import com.google.firebase.FirebaseApp

class RpsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckSetup.install(this)
    }
}
