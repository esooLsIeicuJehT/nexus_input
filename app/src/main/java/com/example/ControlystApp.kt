package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class ControlystApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("ControlystApp", "FirebaseApp initialized successfully.")
        } catch (e: Exception) {
            Log.e("ControlystApp", "Failed to initialize FirebaseApp: ${e.message}", e)
        }
    }
}
