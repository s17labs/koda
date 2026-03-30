package com.s17labs.koda

import android.app.Application

class KodaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = throwable.stackTraceToString()
            DebugLog.crash("Uncaught exception in thread ${thread.name}", stackTrace)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
