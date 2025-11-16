package ro.snapify.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.edit
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("TooManyFunctions")
object DebugLogger {

    private const val MAX_LOG_ENTRIES = 500
    private const val TAG = "ConsoleApp"
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
                val loadedLogs: Array<LogEntry> = gson.fromJson(it, Array<LogEntry>::class.java)
                logEntries.addAll(loadedLogs.toList())
                // Trim to max size
                while (logEntries.size > MAX_LOG_ENTRIES) {
                    logEntries.poll()
                }
            } catch (e: com.google.gson.JsonSyntaxException) {
                error("DebugLogger", "Failed to load persisted logs", e)
            }
        }
    }

    private fun persistLogs() {
        try {
            val logsJson = gson.toJson(logEntries.toList())
            prefs.edit { putString(LOGS_KEY, logsJson) }
        } catch (_: Exception) {
            // Avoid infinite loop if logging fails
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    @Keep
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        // Persist throwable information as a stack trace string instead of the Throwable object
        val throwableStackTrace: String? = null
    ) {
        fun getFormattedMessage(): String {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val formattedTimestamp = sdf.format(Date(timestamp))
            val levelStr = level.name.padEnd(LEVEL_PADDING)
            val tagStr = tag.padEnd(TAG_PADDING)
            val throwableStr = throwableStackTrace?.let { "\n$it" } ?: ""
            return "[$formattedTimestamp] [$levelStr] [$tagStr] $message$throwableStr"
        }
    }

    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val recentLogs = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    fun debug(tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), LogLevel.DEBUG, tag, message, null)
        log(LogLevel.DEBUG, tag, message, null)
        Log.d(TAG, entry.getFormattedMessage())
    }

    fun info(tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), LogLevel.INFO, tag, message, null)
        log(LogLevel.INFO, tag, message, null)
        Log.i(TAG, entry.getFormattedMessage())
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        val throwableStack = throwable?.let { Log.getStackTraceString(it) }
        val entry =
            LogEntry(System.currentTimeMillis(), LogLevel.WARNING, tag, message, throwableStack)
        log(LogLevel.WARNING, tag, message, throwable)
        Log.w(TAG, entry.getFormattedMessage())
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val throwableStack = throwable?.let { Log.getStackTraceString(it) }
        val entry =
            LogEntry(System.currentTimeMillis(), LogLevel.ERROR, tag, message, throwableStack)
        log(LogLevel.ERROR, tag, message, throwable)
        Log.e(TAG, entry.getFormattedMessage())
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val throwableStack = throwable?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwableStack)
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
            appendLine("=== Screenshot Manager Debug Logs ===")
            appendLine(
                "Generated: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date())
                }"
            )
            appendLine("Total Entries: ${'$'}{logEntries.size}")
            appendLine()
            logEntries.forEach { entry ->
                appendLine(entry.getFormattedMessage())
            }
        }
    }
}

