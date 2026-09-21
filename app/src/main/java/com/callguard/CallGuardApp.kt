package com.callguard

import android.app.Application
import android.util.Log

/**
 * Crash handling that never records content: the log gets the exception class and the code locations only. Exception messages
 * are left out because they can quote data. The system's own handler still runs afterwards (the app closes as usual).
 */
class CallGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { Log.e("CallGuard.Crash", describe(e)) }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        /** "IllegalStateException at CallGuardService.refresh:322 < AsrPipeline.run:88" (no message, no data). */
        fun describe(e: Throwable): String {
            val frames = e.stackTrace.filter { it.className.startsWith("com.callguard") }.take(3)
                .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            return e.javaClass.simpleName + if (frames.isEmpty()) "" else " at $frames"
        }
    }
}
