package com.ko.app.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ko.app.R
import com.ko.app.ui.theme.KoTheme
import com.ko.app.ui.theme.ThemeMode
import com.ko.app.util.TimeUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onSelectScreenshotFolder: () -> Unit = {},
    onThemeChangeCallback: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Language changes are handled instantly by MainActivity

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val deletionTime by viewModel.deletionTime.collectAsStateWithLifecycle(initialValue = 15 * 60 * 1000L)
    val isManualMode by viewModel.isManualMode.collectAsStateWithLifecycle(initialValue = false)
    val language by viewModel.language.collectAsStateWithLifecycle(initialValue = "en")
    val screenshotFolderUri by viewModel.screenshotFolderUri.collectAsStateWithLifecycle(
    initialValue = ""
    )
    val autoCleanupEnabled by viewModel.autoCleanupEnabled.collectAsStateWithLifecycle(initialValue = false)

    val formattedFolderPath = remember(screenshotFolderUri) {
        if (screenshotFolderUri.isEmpty()) {
            "Default (Pictures/Screenshots)"
        } else {
            try {
                val decoded = java.net.URLDecoder.decode(screenshotFolderUri, "UTF-8")
                when {
                    decoded.contains("primary:") -> "Primary:" + decoded.substringAfter("primary:")
                        .replace("%2F", "/").replace("%3A", ":")

                    decoded.contains("tree/") -> {
                        val parts = decoded.substringAfter("tree/").split(":")
                        if (parts.size >= 2) {
                            val volume = parts[0]
                            val path = parts[1].replace("%2F", "/").replace("%3A", ":")
                            "$volume:$path"
                        } else decoded
                    }

                    else -> decoded
                }
            } catch (_: Exception) {
                "Custom folder selected"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme Section
            item {
                SettingsSectionHeader(stringResource(R.string.appearance_section))
            }

            item {
                ThemeSelector(
                    currentTheme = currentTheme,
                    onThemeSelected = { theme ->
                        scope.launch {
                            viewModel.setThemeMode(theme)
                            snackbarHostState.showSnackbar("Theme updated")
                        }
                    },
                    onThemeChange = onThemeChangeCallback
                )
            }

            // Language Section
            item {
                SettingsSectionHeader(stringResource(R.string.language_section))
            }

            item {
                LanguageSelector(
                    currentLanguage = language,
                    onLanguageSelected = { lang ->
                        scope.launch {
                            viewModel.setLanguage(lang)
                            // For API 33+, language changes apply immediately
                            // For older APIs, we need to restart
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                snackbarHostState.showSnackbar("Language changed successfully!")
                            } else {
                                snackbarHostState.showSnackbar("Language changed. Restarting app...")
                                // Restart the app
                                val intent =
                                    context.packageManager.getLaunchIntentForPackage(context.packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                (context as? android.app.Activity)?.finish()
                            }
                        }
                    }
                )
            }

            // Behavior Section
            item {
                SettingsSectionHeader(stringResource(R.string.screenshot_management_section))
            }

            item {
                ModeSelector(
                    isManualMode = isManualMode,
                    onModeChanged = { manual ->
                        scope.launch {
                            viewModel.setManualMode(manual)
                        }
                    }
                )
            }

            item {
                DeletionTimeSelector(
                    currentTime = deletionTime,
                    onTimeSelected = { time ->
                        scope.launch {
                            viewModel.setDeletionTime(time)
                        }
                    }
                )
            }

            item {
                AutoCleanupToggle(
                    enabled = autoCleanupEnabled,
                    onToggle = { enabled ->
                        scope.launch {
                            viewModel.setAutoCleanupEnabled(enabled)
                        }
                    }
                )
            }

            // Screenshot Folder Section
            item {
                SettingsSectionHeader(stringResource(R.string.screenshot_folder_section))
            }

            item {
                ScreenshotFolderSelector(
                    currentPath = formattedFolderPath,
                    onSelectFolder = onSelectScreenshotFolder
                )
            }

            // Information Section
            item {
                SettingsSectionHeader(stringResource(R.string.information_section))
            }

            item {
                VersionInfo()
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onThemeChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.theme)) },
            supportingContent = {
                Text(
                    when (currentTheme) {
                        ThemeMode.LIGHT -> stringResource(R.string.light)
                        ThemeMode.DARK -> stringResource(R.string.dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.system_default)
                        ThemeMode.DYNAMIC -> stringResource(R.string.dynamic_colors)
                        ThemeMode.OLED -> stringResource(R.string.oled_theme)
                    }
                )
            },
            trailingContent = {
                Icon(Icons.Default.Palette, contentDescription = null)
            }
        )
    }

    if (showDialog) {
        val isOLED = MaterialTheme.colorScheme.surface == Color.Black
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.choose_theme)) },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = if (isOLED) Modifier.border(
                1.dp,
                Color.White,
                RoundedCornerShape(28.dp)
            ) else Modifier,
            text = {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column {
                        ThemeOption(
                            theme = ThemeMode.LIGHT,
                            title = stringResource(R.string.light),
                            subtitle = stringResource(R.string.always_light_theme),
                            selected = currentTheme == ThemeMode.LIGHT,
                            onThemeChange = onThemeChange,
                            onThemeSelected = onThemeSelected,
                            onDialogDismiss = { showDialog = false }
                        )
                        ThemeOption(
                            theme = ThemeMode.DARK,
                            title = stringResource(R.string.dark),
                            subtitle = stringResource(R.string.always_dark_theme),
                            selected = currentTheme == ThemeMode.DARK,
                            onThemeChange = onThemeChange,
                            onThemeSelected = onThemeSelected,
                            onDialogDismiss = { showDialog = false }
                        )
                        ThemeOption(
                            theme = ThemeMode.SYSTEM,
                            title = stringResource(R.string.system_default),
                            subtitle = stringResource(R.string.follow_system_setting),
                            selected = currentTheme == ThemeMode.SYSTEM,
                            onThemeChange = onThemeChange,
                            onThemeSelected = onThemeSelected,
                            onDialogDismiss = { showDialog = false }
                        )
                        ThemeOption(
                            theme = ThemeMode.DYNAMIC,
                            title = stringResource(R.string.dynamic_colors),
                            subtitle = stringResource(R.string.use_dynamic_colors),
                            selected = currentTheme == ThemeMode.DYNAMIC,
                            onThemeChange = onThemeChange,
                            onThemeSelected = onThemeSelected,
                            onDialogDismiss = { showDialog = false }
                        )
                        ThemeOption(
                            theme = ThemeMode.OLED,
                            title = stringResource(R.string.oled_theme),
                            subtitle = stringResource(R.string.oled_theme_desc),
                            selected = currentTheme == ThemeMode.OLED,
                            onThemeChange = onThemeChange,
                            onThemeSelected = onThemeSelected,
                            onDialogDismiss = { showDialog = false }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    theme: ThemeMode,
    title: String,
    subtitle: String,
    selected: Boolean,
    onThemeChange: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onDialogDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val themeString = when (theme) {
                    ThemeMode.LIGHT -> "light"
                    ThemeMode.DARK -> "dark"
                    ThemeMode.SYSTEM -> "system"
                    ThemeMode.DYNAMIC -> "dynamic"
                    ThemeMode.OLED -> "oled"
                }
                onThemeChange(themeString)
                onThemeSelected(theme)
                onDialogDismiss()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Theme preview
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = when (theme) {
                        ThemeMode.LIGHT -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFFBFE), Color(0xFFE7E0EC))
                        )

                        ThemeMode.DARK -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF141218), Color(0xFF49454F))
                        )

                        ThemeMode.SYSTEM -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFFFBFE), Color(0xFF141218))
                        )

                        ThemeMode.DYNAMIC -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6750A4), Color(0xFF21005D))
                        )

                        ThemeMode.OLED -> Brush.horizontalGradient(
                            colors = listOf(Color(0xFF000000), Color(0xFF1A1A1A))
                        )
                    }
                )
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.language)) },
            supportingContent = {
                Text(
                    when (currentLanguage) {
                        "en" -> stringResource(R.string.english)
                        "ro" -> stringResource(R.string.romanian)
                        else -> stringResource(R.string.english)
                    }
                )
            },
            trailingContent = {
                Icon(Icons.Default.Language, contentDescription = null)
            }
        )
    }

    if (showDialog) {
        val isOLED = MaterialTheme.colorScheme.surface == Color.Black
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.choose_language)) },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = if (isOLED) Modifier.border(
                1.dp,
                Color.White,
                RoundedCornerShape(28.dp)
            ) else Modifier,
            text = {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column {
                        LanguageOption(
                            code = "en",
                            name = stringResource(R.string.english),
                            selected = currentLanguage == "en",
                            onClick = {
                                onLanguageSelected("en")
                                showDialog = false
                            }
                        )
                        LanguageOption(
                            code = "ro",
                            name = stringResource(R.string.romanian),
                            selected = currentLanguage == "ro",
                            onClick = {
                                onLanguageSelected("ro")
                                showDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(
    code: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = code.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ModeSelector(
    isManualMode: Boolean,
    onModeChanged: (Boolean) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.operation_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = isManualMode,
                    onClick = { onModeChanged(true) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.manual_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.manual_mode_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = !isManualMode,
                    onClick = { onModeChanged(false) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.automatic_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.automatic_mode_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletionTimeSelector(
    currentTime: Long,
    onTimeSelected: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }

    // Update customMinutes when dialog opens - use fresh value from currentTime
    LaunchedEffect(showDialog) {
        if (showDialog) {
            val minutes = (currentTime / (60 * 1000L))
            customMinutes = minutes.toString()
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.deletion_time)) },
            supportingContent = {
                Text(TimeUtils.formatDeletionTime(currentTime))
            },
            trailingContent = {
                Icon(Icons.Default.Schedule, contentDescription = null)
            }
        )
    }

    if (showDialog) {
        val isOLED = MaterialTheme.colorScheme.surface == Color.Black
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.choose_deletion_time_title)) },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = if (isOLED) Modifier.border(
                1.dp,
                Color.White,
                RoundedCornerShape(28.dp)
            ) else Modifier,
            text = {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Custom time input section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Custom Time (minutes)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom minutes input
                                OutlinedTextField(
                                    value = customMinutes,
                                    onValueChange = { value ->
                                        // Only allow numeric input
                                        if (value.isEmpty() || value.all { it.isDigit() }) {
                                            customMinutes = value
                                        }
                                    },
                                    label = { Text("Minutes") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick add buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    QuickTimeButton("+1h", 60) { minutesToAdd ->
                                        val current = customMinutes.toLongOrNull() ?: 0
                                        customMinutes = (current + minutesToAdd).toString()
                                    }
                                    QuickTimeButton("+1d", 24 * 60) { minutesToAdd ->
                                        val current = customMinutes.toLongOrNull() ?: 0
                                        customMinutes = (current + minutesToAdd).toString()
                                    }
                                    QuickTimeButton("+1w", 7 * 24 * 60) { minutesToAdd ->
                                        val current = customMinutes.toLongOrNull() ?: 0
                                        customMinutes = (current + minutesToAdd).toString()
                                    }
                                    QuickTimeButton("+1m", 30 * 24 * 60) { minutesToAdd ->
                                        val current = customMinutes.toLongOrNull() ?: 0
                                        customMinutes = (current + minutesToAdd).toString()
                                    }
                                    QuickTimeButton("+1y", 365 * 24 * 60) { minutesToAdd ->
                                        val current = customMinutes.toLongOrNull() ?: 0
                                        customMinutes = (current + minutesToAdd).toString()
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Apply custom time button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val minutes = customMinutes.toLongOrNull() ?: 1
                                            val milliseconds = minutes * 60 * 1000L
                                            onTimeSelected(milliseconds)
                                            showDialog = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = customMinutes.isNotEmpty() && customMinutes.toLongOrNull() != null && customMinutes.toLong() > 0
                                    ) {
                                        Text("Set Custom Time")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            // Reset to default 1 minute
                                            val defaultMs = 1 * 60 * 1000L
                                            onTimeSelected(defaultMs)
                                            showDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Reset to 1 min")
                                    }
                                }
                            }
                        }

                        // Predefined options
                        Text(
                            text = "Quick Options",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val timeOptions = listOf(
                            5 * 60 * 1000L to stringResource(R.string.five_minutes),
                            15 * 60 * 1000L to stringResource(R.string.fifteen_minutes),
                            30 * 60 * 1000L to stringResource(R.string.thirty_minutes),
                            1 * 60 * 60 * 1000L to stringResource(R.string.one_hour),
                            2 * 60 * 60 * 1000L to stringResource(R.string.two_hours),
                            6 * 60 * 60 * 1000L to stringResource(R.string.six_hours),
                            12 * 60 * 60 * 1000L to stringResource(R.string.twelve_hours),
                            24 * 60 * 60 * 1000L to stringResource(R.string.one_day),
                            3 * 24 * 60 * 60 * 1000L to stringResource(R.string.three_days),
                            7 * 24 * 60 * 60 * 1000L to stringResource(R.string.one_week)
                        )

                        LazyColumn {
                            items(timeOptions) { (time, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onTimeSelected(time)
                                            showDialog = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentTime == time,
                                        onClick = {
                                            onTimeSelected(time)
                                            showDialog = false
                                        }
                                    )
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ScreenshotFolderSelector(
    currentPath: String,
    onSelectFolder: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.screenshot_folder)) },
            supportingContent = {
                Text(currentPath)
            },
            trailingContent = {
                Icon(Icons.Default.Folder, contentDescription = null)
            },
            modifier = Modifier.clickable(onClick = onSelectFolder)
        )
    }
}

@Composable
private fun QuickTimeButton(
    label: String,
    minutesToAdd: Long,
    onTimeAdd: (Long) -> Unit
) {
    OutlinedButton(
        onClick = { onTimeAdd(minutesToAdd) },
        modifier = Modifier.width(48.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoCleanupToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text("Auto Cleanup") },
            supportingContent = {
                Text(
                    "Automatically delete expired screenshots daily",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
        )
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Auto Cleanup Toggle")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCleanupTogglePreview() {
    KoTheme {
        AutoCleanupToggle(
            enabled = true,
            onToggle = {}
        )
    }
}

@Composable
private fun VersionInfo() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Screenshot Manager",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "MIT License \nCopyright (c) 2025",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\nMade with ❤\uFE0F by Adalbert Alexandru Ungureanu \nDonation are welcome \uD83D\uDE4F",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Preview functions
@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Settings Screen")
@Composable
fun SettingsScreenPreview() {
    KoTheme {
        SettingsScreen()
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Theme Selector")
@Composable
fun ThemeSelectorPreview() {
    KoTheme {
        ThemeSelector(
            currentTheme = ThemeMode.SYSTEM,
            onThemeSelected = {},
            onThemeChange = {}
        )
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Language Selector")
@Composable
fun LanguageSelectorPreview() {
    KoTheme {
        LanguageSelector(
            currentLanguage = "en",
            onLanguageSelected = {}
        )
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Mode Selector")
@Composable
fun ModeSelectorPreview() {
    KoTheme {
        ModeSelector(
            isManualMode = true,
            onModeChanged = {}
        )
    }
}

@Preview(showBackground = true, device = Devices.PIXEL_4, name = "Deletion Time Selector")
@Composable
fun DeletionTimeSelectorPreview() {
    KoTheme {
        DeletionTimeSelector(
            currentTime = 1 * 60 * 1000L, // 15 minutes
            onTimeSelected = {}
        )
    }
}
