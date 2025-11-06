package com.ko.app.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getString
import com.ko.app.LanguageManager
import com.ko.app.R
import com.ko.app.data.preferences.AppPreferences
import com.ko.app.ui.theme.KoTheme
import com.ko.app.ui.theme.ThemeMode
import com.ko.app.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class DebugConsoleActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    private val logListener: (DebugLogger.LogEntry) -> Unit = { _ ->
        // Will be handled by recomposition
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeModeString by preferences.themeMode.collectAsState(initial = "system")
            val themeMode = when (themeModeString) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "dynamic" -> ThemeMode.DYNAMIC
                else -> ThemeMode.SYSTEM
            }

            val language by preferences.language.collectAsState(initial = LanguageManager.getCurrentLanguage())

            val localeTag = if (language == "ro") "ro" else "en"
            val newLocale = Locale.forLanguageTag(localeTag)

            @Suppress("DEPRECATION")
            resources.updateConfiguration(
                resources.configuration.apply { setLocale(newLocale) },
                resources.displayMetrics
            )

            CompositionLocalProvider(LocalConfiguration provides Configuration(resources.configuration)) {
                key(language) {
                    KoTheme(themeMode = themeMode) {
                        DebugConsoleScreen(onNavigateBack = { finish() })
                    }
                }
            }
        }

        DebugLogger.addListener(logListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.removeListener(logListener)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var debugChecked by remember { mutableStateOf(true) }
    var infoChecked by remember { mutableStateOf(true) }
    var warningChecked by remember { mutableStateOf(true) }
    var errorChecked by remember { mutableStateOf(true) }

    val allLogs = remember {
        mutableStateOf(
            DebugLogger.getAllLogs().ifEmpty { DebugLogger.getRecentLogs() })
    }
    val filteredLogs =
        remember(debugChecked, infoChecked, warningChecked, errorChecked, allLogs.value) {
            allLogs.value.filter { entry ->
                when (entry.level) {
                    DebugLogger.LogLevel.DEBUG -> debugChecked
                    DebugLogger.LogLevel.INFO -> infoChecked
                    DebugLogger.LogLevel.WARNING -> warningChecked
                    DebugLogger.LogLevel.ERROR -> errorChecked
                }
            }
        }

    val totalText =
        if (DebugLogger.getAllLogs().isEmpty()) stringResource(R.string.recent) else stringResource(
            R.string.total
        )
    val logCountText =
        "${filteredLogs.size} ${stringResource(R.string.log_entries_count)} (${allLogs.value.size} $totalText)"

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when logs change
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    // Update logs periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Update every second
            allLogs.value = DebugLogger.getAllLogs().ifEmpty { DebugLogger.getRecentLogs() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        AlertDialog.Builder(context)
                            .setTitle("Clear Logs")
                            .setMessage("Are you sure you want to clear all logs?")
                            .setPositiveButton("Clear") { _, _ ->
                                DebugLogger.clearLogs()
                                allLogs.value = emptyList()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.clear))
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val logsContent = DebugLogger.exportLogsAsString()
                                val timestamp =
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                                        Date()
                                    )
                                val fileName = "ko_debug_logs_$timestamp.txt"

                                withContext(Dispatchers.IO) {
                                    val downloadsDir =
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val file = File(downloadsDir, fileName)
                                    file.writeText(logsContent)
                                }

                                AlertDialog.Builder(context)
                                    .setTitle("Logs Exported")
                                    .setMessage("Logs have been exported to:\nDownloads/$fileName")
                                    .setPositiveButton("OK", null)
                                    .setNeutralButton("Share") { _, _ ->
                                        shareLogs(context, logsContent)
                                    }
                                    .show()
                            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                                AlertDialog.Builder(context)
                                    .setTitle("Export Failed")
                                    .setMessage("Failed to export logs: ${e.message}")
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export))
                }

                OutlinedButton(
                    onClick = {
                        val logsContent = DebugLogger.exportLogsAsString()
                        val clipboard =
                            context.getSystemService(android.content.ClipboardManager::class.java)
                        val clip =
                            android.content.ClipData.newPlainText("Ko Debug Logs", logsContent)
                        clipboard.setPrimaryClip(clip)

                        android.widget.Toast.makeText(
                            context,
                            getString(context, R.string.logs_copied),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.copy))
                }
            }

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = debugChecked,
                    onClick = { debugChecked = !debugChecked },
                    label = { Text(stringResource(R.string.debug)) }
                )
                FilterChip(
                    selected = infoChecked,
                    onClick = { infoChecked = !infoChecked },
                    label = { Text(stringResource(R.string.info)) }
                )
                FilterChip(
                    selected = warningChecked,
                    onClick = { warningChecked = !warningChecked },
                    label = { Text(stringResource(R.string.warning)) }
                )
                FilterChip(
                    selected = errorChecked,
                    onClick = { errorChecked = !errorChecked },
                    label = { Text(stringResource(R.string.error)) }
                )
            }

            // Log count
            Text(
                text = logCountText,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Logs list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(8.dp)
            ) {
                items(filteredLogs) { entry ->
                    val color = when (entry.level) {
                        DebugLogger.LogLevel.DEBUG -> Color(0xFFAAAAAA)
                        DebugLogger.LogLevel.INFO -> Color(0xFF4CAF50)
                        DebugLogger.LogLevel.WARNING -> Color(0xFFFF9800)
                        DebugLogger.LogLevel.ERROR -> Color(0xFFF44336)
                    }
                    Text(
                        text = entry.getFormattedMessage(),
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun shareLogs(context: android.content.Context, logsContent: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Ko Screenshot App Debug Logs")
        putExtra(Intent.EXTRA_TEXT, logsContent)
    }
    context.startActivity(Intent.createChooser(intent, "Share Logs"))
}
