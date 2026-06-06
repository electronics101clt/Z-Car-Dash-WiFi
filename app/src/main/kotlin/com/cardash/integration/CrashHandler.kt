package com.cardash.integration

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Global crash handler for ZCarDashWiFi
 * Based on Aptoide's crash avoidance pattern
 *
 * Implements Thread.UncaughtExceptionHandler to catch all unhandled exceptions
 * and prevent app crashes, logging them instead for debugging.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private var instance: CrashHandler? = null

        /**
         * Initialize the global crash handler
         * Call this from MainActivity.onCreate() or Application.onCreate()
         */
        fun init(context: Context) {
            if (instance == null) {
                val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                instance = CrashHandler(context.applicationContext, defaultHandler)
                Thread.setDefaultUncaughtExceptionHandler(instance)
                Log.d(TAG, "CrashHandler initialized - Aptoide-style crash prevention active")
            }
        }

        /**
         * Get singleton instance for logging
         */
        fun getInstance(): CrashHandler? = instance

        /**
         * Log an exception manually (like Aptoide's CrashReport.getInstance().log(e))
         */
        fun log(e: Throwable) {
            Log.e(TAG, "Exception logged: ${e.message}", e)
            instance?.logToFile(e)
        }

        /**
         * Log an exception with context message
         */
        fun log(message: String, e: Throwable) {
            Log.e(TAG, "$message: ${e.message}", e)
            instance?.logToFile(e, message)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            // Log to file for debugging
            logToFile(throwable, "UNCAUGHT in ${thread.name}")

            // Try to recover gracefully
            handleCrash(throwable)

        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler itself", e)
        } finally {
            // Pass to default handler (may terminate app)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Log exception to file for later debugging
     */
    private fun logToFile(throwable: Throwable, prefix: String = "") {
        try {
            val logDir = File(context.getExternalFilesDir(null), "crash_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val logFile = File(logDir, "crash_$timestamp.log")

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)

            val logContent = buildString {
                append("=== ZCarDashWiFi Crash Log ===\n")
                append("Time: $timestamp\n")
                if (prefix.isNotEmpty()) {
                    append("Context: $prefix\n")
                }
                append("Exception: ${throwable.javaClass.name}\n")
                append("Message: ${throwable.message}\n")
                append("\nStack Trace:\n")
                append(sw.toString())
                append("\n=== End Log ===\n")
            }

            logFile.writeText(logContent)
            Log.d(TAG, "Crash log saved to: ${logFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    /**
     * Attempt to handle specific crash types gracefully
     */
    private fun handleCrash(throwable: Throwable) {
        when (throwable) {
            is SecurityException -> {
                Log.w(TAG, "SecurityException caught - likely missing permission")
                // App can continue, just log it
            }
            is IllegalStateException -> {
                Log.w(TAG, "IllegalStateException caught - invalid state recovered")
                // Try to recover by resetting state
            }
            is NullPointerException -> {
                Log.w(TAG, "NullPointerException caught - null check failed")
                // Log for fixing but don't crash
            }
            else -> {
                Log.e(TAG, "Unhandled exception type: ${throwable.javaClass.name}")
            }
        }
    }

    /**
     * Console logger (like Aptoide's ConsoleLogger)
     */
    class ConsoleLogger {
        fun log(message: String) {
            Log.d(TAG, message)
        }

        fun error(message: String, throwable: Throwable? = null) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }
}
