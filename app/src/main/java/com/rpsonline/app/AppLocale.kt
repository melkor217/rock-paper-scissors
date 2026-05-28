package com.rpsonline.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Forces English resources regardless of device language. */
object AppLocale {
    private val ENGLISH = Locale.ENGLISH

    fun wrap(context: Context): Context {
        Locale.setDefault(ENGLISH)
        val config = Configuration(context.resources.configuration)
        config.setLocale(ENGLISH)
        return context.createConfigurationContext(config)
    }
}
