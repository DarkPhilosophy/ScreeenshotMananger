package com.ko.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object DebugLogger {

    private const val MAX_LOG_ENTRIES = 500
    private const val TAG = "KoScreenshot"
    private const val LEVEL_PADDING = 7
    private const val TAG_PADDING = 30
    private const val PREFS_NAME = "debug_logs"
    private const val LOGS_KEY = "logs"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedLogs()
    }

    private fun loadPersistedLogs() {
        val logsJson = prefs.getString(LOGS_KEY, null)
        logsJson?.let {
            try {
                val loadedLogs: List<LogEntry> = gson.fromJson(it, object : TypeToken<List<LogEntry>>() {}.type)
                logEntries.addAll(loadedLogs)
                // Trim to max size
                while (logEntries.size > MAX_LOG_ENTRIES) {
                    logEntries.poll()
                }
            } catch (e: Exception) {
                error("DebugLogger", "Failed to load persisted logs", e)
            }
        }
    }

    private fun persistLogs() {
        try {
            val logsJson = gson.toJson(logEntries.toList())
            prefs.edit().putString(LOGS_KEY, logsJson).apply()
        } catch (e: Exception) {
            // Avoid infinite loop if logging fails
        }
    }

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
            val levelStr = level.name.padEnd(LEVEL_PADDING)
            val tagStr = tag.padEnd(TAG_PADDING)
            val throwableStr = throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            return "[${getFormattedTimestamp()}] [$levelStr] [$tagStr] $message$throwableStr"
        }
    }

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val recentLogs = mutableListOf<LogEntry>()
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

        persistLogs()

        // Notify listeners
        synchronized(listeners) {
            listeners.forEach { it(entry) }
        }
    }

    fun getAllLogs(): List<LogEntry> {
        return logEntries.toList()
    }

    fun getRecentLogs(): List<LogEntry> {
        return recentLogs.toList()
    }

    fun clearLogs() {
        logEntries.clear()
        persistLogs()
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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine("Total Entries: ${logEntries.size}")
            appendLine()
            logEntries.forEach { entry ->
                appendLine(entry.getFormattedMessage())
            }
        }
    }
}
