package com.example

import android.app.Application
import android.util.Log
import com.example.injector.NexusRuntimeContext
import com.google.firebase.FirebaseApp

class ControlystApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NexusRuntimeContext.initialize(this)
        try {
            val initialized = FirebaseApp.initializeApp(this)
            if (initialized == null) Log.w("NexusApp", "Firebase is not configured; cloud features are unavailable")
            else Log.d("NexusApp", "Firebase initialized")
        } catch (e: Exception) {
            Log.e("NEXUS INPUTApp", "Failed to initialize FirebaseApp: ${e.message}", e)
        }
    }
}
