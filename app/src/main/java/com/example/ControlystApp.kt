package com.example

import android.app.Application
import com.example.injector.NexusRuntimeContext

class ControlystApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NexusRuntimeContext.initialize(this)
    }
}
