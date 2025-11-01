package com.ko.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ko.app.databinding.ActivityDebugConsoleBinding
import com.ko.app.ui.adapter.LogAdapter
import com.ko.app.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugConsoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugConsoleBinding
    private lateinit var adapter: LogAdapter
    private val logListener: (DebugLogger.LogEntry) -> Unit = { entry ->
        runOnUiThread {
            updateLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        setupFilters()
        updateLogs()

        DebugLogger.addListener(logListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.removeListener(logListener)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = LogAdapter()
        binding.logsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DebugConsoleActivity).apply {
                stackFromEnd = true
            }
            adapter = this@DebugConsoleActivity.adapter
        }
    }

    private fun setupButtons() {
        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to clear all logs?")
                .setPositiveButton("Clear") { _, _ ->
                    DebugLogger.clearLogs()
                    updateLogs()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnExportLogs.setOnClickListener {
            exportLogs()
        }
    }

    private fun setupFilters() {
        val updateFilter = {
            updateLogs()
        }

        binding.chipDebug.setOnCheckedChangeListener { _, _ -> updateFilter() }
        binding.chipInfo.setOnCheckedChangeListener { _, _ -> updateFilter() }
        binding.chipWarning.setOnCheckedChangeListener { _, _ -> updateFilter() }
        binding.chipError.setOnCheckedChangeListener { _, _ -> updateFilter() }
    }

    private fun updateLogs() {
        val allLogs = DebugLogger.getAllLogs().ifEmpty { DebugLogger.getRecentLogs() }
        val filteredLogs = allLogs.filter { entry ->
            when (entry.level) {
                DebugLogger.LogLevel.DEBUG -> binding.chipDebug.isChecked
                DebugLogger.LogLevel.INFO -> binding.chipInfo.isChecked
                DebugLogger.LogLevel.WARNING -> binding.chipWarning.isChecked
                DebugLogger.LogLevel.ERROR -> binding.chipError.isChecked
            }
        }

        adapter.submitList(filteredLogs)
        val totalText = if (DebugLogger.getAllLogs().isEmpty()) "recent" else "total"
        binding.logCountText.text = "${filteredLogs.size} log entries (${allLogs.size} $totalText)"

        // Auto-scroll to bottom
        if (filteredLogs.isNotEmpty()) {
            binding.logsRecyclerView.scrollToPosition(filteredLogs.size - 1)
        }
    }

    private fun exportLogs() {
        lifecycleScope.launch {
            try {
                val logsContent = DebugLogger.exportLogsAsString()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "ko_debug_logs_$timestamp.txt"

                withContext(Dispatchers.IO) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    file.writeText(logsContent)
                }

                AlertDialog.Builder(this@DebugConsoleActivity)
                    .setTitle("Logs Exported")
                    .setMessage("Logs have been exported to:\nDownloads/$fileName")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Share") { _, _ ->
                        shareLogs(logsContent)
                    }
                    .show()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                AlertDialog.Builder(this@DebugConsoleActivity)
                    .setTitle("Export Failed")
                    .setMessage("Failed to export logs: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun shareLogs(logsContent: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ko Screenshot App Debug Logs")
            putExtra(Intent.EXTRA_TEXT, logsContent)
        }
        startActivity(Intent.createChooser(intent, "Share Logs"))
    }
}
