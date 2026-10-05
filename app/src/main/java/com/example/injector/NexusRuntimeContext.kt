package com.example.injector

import android.content.Context

/**
 * Process-wide application Context for injector backends that are created by the
 * existing no-argument factory surface.
 *
 * It is initialized from ControlystApp.onCreate(). Access before initialization
 * is a hard error so a broken lifecycle cannot silently fall back to another
 * backend.
 */
object NexusRuntimeContext {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun require(): Context = applicationContext
        ?: throw IllegalStateException(
            "NEXUS runtime Context is not initialized. ControlystApp.onCreate() must run before injector creation."
        )
}
