package ro.snapify.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.snapify.LanguageManager
import ro.snapify.R
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.ui.theme.AppTheme
import ro.snapify.ui.theme.ThemeMode
import ro.snapify.util.DebugLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.compose.material.icons.Icons as ComposeIcons

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
                    AppTheme(themeMode = themeMode) {
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
    val snackbarHostState = remember { SnackbarHostState() }

    var debugChecked by remember { mutableStateOf(true) }
    var infoChecked by remember { mutableStateOf(true) }
    var warningChecked by remember { mutableStateOf(true) }
    var errorChecked by remember { mutableStateOf(true) }
    var customText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val allLogs = remember {
        mutableStateOf(
            DebugLogger.getAllLogs().ifEmpty { DebugLogger.getRecentLogs() })
    }
    val filteredLogs =
        remember(
            debugChecked,
            infoChecked,
            warningChecked,
            errorChecked,
            customText,
            useRegex,
            allLogs.value
        ) {
            allLogs.value.filter { entry ->
                val levelMatch = when (entry.level) {
                    DebugLogger.LogLevel.DEBUG -> debugChecked
                    DebugLogger.LogLevel.INFO -> infoChecked
                    DebugLogger.LogLevel.WARNING -> warningChecked
                    DebugLogger.LogLevel.ERROR -> errorChecked
                }
                val textMatch = if (customText.isNotEmpty()) {
                    if (useRegex) {
                        try {
                            Regex(customText).containsMatchIn(entry.getFormattedMessage())
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        entry.getFormattedMessage().contains(customText, ignoreCase = true)
                    }
                } else true
                levelMatch && textMatch
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showFilterDialog = true }) {
                Icon(ComposeIcons.Filled.FilterList, contentDescription = "Filters")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.clear))
                }

                OutlinedButton(
                    onClick = { showExportDialog = true },
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
                            android.content.ClipData.newPlainText(
                                "Screenshot Manager Debug Logs",
                                logsContent
                            )
                        clipboard.setPrimaryClip(clip)

                        scope.launch {
                            snackbarHostState.showSnackbar(getString(context, R.string.logs_copied))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.copy))
                }
            }

            // Filter Dialog
            if (showFilterDialog) {
                val isOLED = MaterialTheme.colorScheme.surface == Color.Black
                AlertDialog(
                    onDismissRequest = { showFilterDialog = false },
                    title = { Text("Log Filters") },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = if (isOLED) Modifier.border(
                        1.dp,
                        Color.White,
                        RoundedCornerShape(28.dp)
                    ) else Modifier,
                    text = {
                        MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                            Column {
                                Text("Log Levels:")
                                Row {
                                    Checkbox(
                                        checked = debugChecked,
                                        onCheckedChange = { debugChecked = it })
                                    Text("Debug", modifier = Modifier.padding(start = 8.dp))
                                }
                                Row {
                                    Checkbox(
                                        checked = infoChecked,
                                        onCheckedChange = { infoChecked = it })
                                    Text("Info", modifier = Modifier.padding(start = 8.dp))
                                }
                                Row {
                                    Checkbox(
                                        checked = warningChecked,
                                        onCheckedChange = { warningChecked = it })
                                    Text("Warning", modifier = Modifier.padding(start = 8.dp))
                                }
                                Row {
                                    Checkbox(
                                        checked = errorChecked,
                                        onCheckedChange = { errorChecked = it })
                                    Text("Error", modifier = Modifier.padding(start = 8.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(
                                    value = customText,
                                    onValueChange = { customText = it },
                                    label = { Text("Custom search") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row {
                                    Checkbox(
                                        checked = useRegex,
                                        onCheckedChange = { useRegex = it })
                                    Text("Use regex", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        OutlinedButton(onClick = { showFilterDialog = false }) {
                            Text("OK")
                        }
                    }
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
            SelectionContainer {
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

        // Clear Logs Dialog
        if (showClearDialog) {
            val isOLED = MaterialTheme.colorScheme.surface == Color.Black
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Logs") },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = if (isOLED) Modifier.border(
                    1.dp,
                    Color.White,
                    RoundedCornerShape(28.dp)
                ) else Modifier,
                text = { Text("Are you sure you want to clear all logs?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            DebugLogger.clearLogs()
                            allLogs.value = emptyList()
                            showClearDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Logs cleared")
                            }
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Export Logs Dialog
        if (showExportDialog) {
            val isOLED = MaterialTheme.colorScheme.surface == Color.Black
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export Logs") },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = if (isOLED) Modifier.border(
                    1.dp,
                    Color.White,
                    RoundedCornerShape(28.dp)
                ) else Modifier,
                text = { Text("Export debug logs to Downloads folder?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val logsContent = DebugLogger.exportLogsAsString()
                                    val timestamp =
                                        SimpleDateFormat(
                                            "yyyyMMdd_HHmmss",
                                            Locale.getDefault()
                                        ).format(
                                            Date()
                                        )
                                    val fileName = "screenshot_manager_debug_logs_$timestamp.txt"

                                    withContext(Dispatchers.IO) {
                                        val downloadsDir =
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                            )
                                        val file = File(downloadsDir, fileName)
                                        file.writeText(logsContent)
                                    }

                                    showExportDialog = false
                                    snackbarHostState.showSnackbar("Logs exported to Downloads/$fileName")
                                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                                    showExportDialog = false
                                    snackbarHostState.showSnackbar("Export failed: ${e.message}")
                                }
                            }
                        }
                    ) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun shareLogs(context: android.content.Context, logsContent: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Screenshot Manager Debug Logs")
        putExtra(Intent.EXTRA_TEXT, logsContent)
    }
    context.startActivity(Intent.createChooser(intent, "Share Logs"))
}
