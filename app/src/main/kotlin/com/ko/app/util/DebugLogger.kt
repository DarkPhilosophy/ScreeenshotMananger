package com.ko.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object DebugLogger {

    private const val MAX_LOG_ENTRIES = 500
    private const val TAG = "KoScreenshot"

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        private fun getFormattedTimestamp(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun getFormattedMessage(): String {
            val levelStr = level.name.padEnd(7)
            val tagStr = tag.padEnd(30)
            val throwableStr = throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            return "[${getFormattedTimestamp()}] [$levelStr] [$tagStr] $message$throwableStr"
        }
    }

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    fun debug(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
        Log.d(TAG, "[$tag] $message")
    }

    fun info(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
        Log.i(TAG, "[$tag] $message")
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARNING, tag, message, throwable)
        if (throwable != null) {
            Log.w(TAG, "[$tag] $message", throwable)
        } else {
            Log.w(TAG, "[$tag] $message")
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
        if (throwable != null) {
            Log.e(TAG, "[$tag] $message", throwable)
        } else {
            Log.e(TAG, "[$tag] $message")
        }
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        logEntries.add(entry)

        // Trim to max size
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }

        // Notify listeners
        synchronized(listeners) {
            listeners.forEach { it(entry) }
        }
    }

    fun getAllLogs(): List<LogEntry> {
        return logEntries.toList()
    }

    fun clearLogs() {
        logEntries.clear()
        info("DebugLogger", "Logs cleared")
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun exportLogsAsString(): String {
        return buildString {
            appendLine("=== Ko Screenshot App Debug Logs ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Total Entries: ${logEntries.size}")
            appendLine()
            logEntries.forEach { entry ->
                appendLine(entry.getFormattedMessage())
            }
        }
    }
}

