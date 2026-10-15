package com.titanpro.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class TitanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start Chaquopy Python runtime as early as possible
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
