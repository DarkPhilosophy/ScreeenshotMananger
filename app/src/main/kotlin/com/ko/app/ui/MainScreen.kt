package com.ko.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ko.app.R
import com.ko.app.data.entity.Screenshot
import com.ko.app.ui.theme.ErrorRed
import com.ko.app.ui.theme.KoTheme
import com.ko.app.ui.theme.SuccessGreen
import com.ko.app.ui.theme.WarningOrange
import com.ko.app.util.DebugLogger
import com.ko.app.util.TimeUtils
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenshotTab {
    MARKED, KEPT, UNMARKED, ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    viewModel.onNavigateToSettings = onNavigateToSettings

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = MainUiState())
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle(initialValue = ScreenshotTab.ALL)
    val screenshots by viewModel.screenshots.collectAsStateWithLifecycle(initialValue = emptyList())
    val refreshTrigger by viewModel.refreshTrigger.collectAsStateWithLifecycle(initialValue = 0L)
        .also {
            DebugLogger.info("MainScreen", "RefreshTrigger collected: $it")
        }
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    val monitoringStatus by viewModel.monitoringStatus.collectAsStateWithLifecycle(initialValue = MonitoringStatus.STOPPED)

    val context = LocalContext.current

    // Check if all permissions are granted to hide permission button
    var allPermissionsGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val readPerm =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        
        val permissions = listOf(
            readPerm,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
            "overlay",
            "battery"
        ).filterNotNull()

        updatePermissionStatuses(context, permissions.map { "temp" to it }) {
            allPermissionsGranted = it.values.all { granted -> granted }
        }
    }

    val listState = rememberLazyListState()

    // Show snackbar for status messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessage()
            }
        }
    }

    val filteredScreenshots = remember(screenshots, currentTab, refreshTrigger) {
        val filtered = when (currentTab) {
            ScreenshotTab.ALL -> screenshots
            ScreenshotTab.MARKED -> screenshots.filter { it.deletionTimestamp != null && !it.isKept }
            ScreenshotTab.KEPT -> screenshots.filter { it.isKept }
            ScreenshotTab.UNMARKED -> screenshots.filter { it.deletionTimestamp == null && !it.isKept }
        }
        DebugLogger.info(
            "MainScreen",
            "Filtered screenshots: ${filtered.size} for tab $currentTab, refreshTrigger: $refreshTrigger"
        )
        filtered
    }

    // Auto-scroll to top when tab changes or new screenshots are added
    LaunchedEffect(currentTab) {
        listState.animateScrollToItem(0)
    }

    // Auto-scroll to top when new screenshots are added (newest first)
    LaunchedEffect(refreshTrigger) {
        if (filteredScreenshots.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    IconButton(onClick = {
                        when (monitoringStatus) {
                            MonitoringStatus.STOPPED -> viewModel.startMonitoring()
                            MonitoringStatus.ACTIVE -> viewModel.stopMonitoring()
                            MonitoringStatus.MISSING_PERMISSIONS -> viewModel.startMonitoring() // Will check permissions and start or show dialog
                        }
                    }) {
                        val (icon, color) = when (monitoringStatus) {
                            MonitoringStatus.STOPPED -> Icons.Default.PlayArrow to ErrorRed
                            MonitoringStatus.ACTIVE -> Icons.Default.Pause to SuccessGreen
                            MonitoringStatus.MISSING_PERMISSIONS -> Icons.Default.Pause to WarningOrange
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = when (monitoringStatus) {
                                MonitoringStatus.STOPPED -> stringResource(R.string.start_service)
                                MonitoringStatus.ACTIVE -> stringResource(R.string.stop_service)
                                MonitoringStatus.MISSING_PERMISSIONS -> stringResource(R.string.grant_permissions)
                            },
                            tint = color
                        )
                    }
                    IconButton(onClick = { viewModel.refreshScreenshots() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                    if (!allPermissionsGranted) {
                        IconButton(onClick = { viewModel.showPermissionsDialog() }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.permissions)
                            )
                        }
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateToSettings() },
                icon = { Icon(Icons.Default.Settings, stringResource(R.string.settings_button)) },
                text = { Text(stringResource(R.string.settings_button)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Service status indicator
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                val (backgroundColor, statusText) = when (monitoringStatus) {
                    MonitoringStatus.STOPPED -> ErrorRed to stringResource(R.string.monitoring_stopped)
                    MonitoringStatus.ACTIVE -> SuccessGreen to stringResource(R.string.monitoring_active)
                    MonitoringStatus.MISSING_PERMISSIONS -> WarningOrange to stringResource(R.string.missing_permissions)
                }
                Surface(
                    color = backgroundColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = monitoringStatus == MonitoringStatus.MISSING_PERMISSIONS) {
                            viewModel.showPermissionsDialog()
                        }
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Tab row
            PrimaryTabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                ScreenshotTab.entries.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = when (tab) {
                                    ScreenshotTab.MARKED -> stringResource(R.string.tab_marked)
                                    ScreenshotTab.KEPT -> stringResource(R.string.tab_kept)
                                    ScreenshotTab.UNMARKED -> stringResource(R.string.unmarked)
                                    ScreenshotTab.ALL -> stringResource(R.string.tab_all)
                                }
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    ScreenshotTab.MARKED -> Icons.Default.Schedule
                                    ScreenshotTab.KEPT -> Icons.Default.Star
                                    ScreenshotTab.UNMARKED -> Icons.Default.CheckCircle
                                    ScreenshotTab.ALL -> Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            // Content
            when {
                uiState.isLoading && filteredScreenshots.isEmpty() -> {
                    LoadingScreen()
                }

                filteredScreenshots.isEmpty() -> {
                    EmptyStateScreen(currentTab)
                }

                else -> {
                    ScreenshotList(
                        screenshots = filteredScreenshots,
                        listState = listState,
                        currentTime = currentTime,
                        isLoading = uiState.isLoading,
                        onScreenshotClick = { viewModel.openScreenshot(it) },
                        onKeepClick = { viewModel.keepScreenshot(it) },
                        onDeleteClick = { viewModel.deleteScreenshot(it) },
                        onLoadMore = { viewModel.loadMoreScreenshots() }
                    )
                }
            }
        }
    }

    // Permission dialog
    if (uiState.showPermissionDialog) {
        PermissionDialog(
            onDismiss = { viewModel.hidePermissionsDialog() },
            onPermissionsUpdated = { viewModel.refreshMonitoringStatus() }
        )
    }

    // Welcome dialog for first launch
    if (uiState.showWelcomeDialog) {
        WelcomeDialog(
            onDismiss = { viewModel.dismissWelcomeDialog() }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.loading_screenshots),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EmptyStateScreen(tab: ScreenshotTab) {
    val icon = when (tab) {
        ScreenshotTab.MARKED -> Icons.Default.Schedule
        ScreenshotTab.KEPT -> Icons.Default.Star
        ScreenshotTab.UNMARKED -> Icons.Default.CheckCircle
        ScreenshotTab.ALL -> Icons.Default.PhotoLibrary
    }

    val title = when (tab) {
        ScreenshotTab.MARKED -> stringResource(R.string.no_marked_screenshots)
        ScreenshotTab.KEPT -> stringResource(R.string.no_kept_screenshots)
        ScreenshotTab.UNMARKED -> stringResource(R.string.no_unmarked_screenshots)
        ScreenshotTab.ALL -> stringResource(R.string.no_screenshots_found)
    }

    val subtitle = when (tab) {
        ScreenshotTab.MARKED -> stringResource(R.string.marked_screenshots_description)
        ScreenshotTab.KEPT -> stringResource(R.string.kept_screenshots_description)
        ScreenshotTab.UNMARKED -> stringResource(R.string.unmarked_screenshots_description)
        ScreenshotTab.ALL -> stringResource(R.string.take_screenshot_to_start)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScreenshotList(
    screenshots: List<Screenshot>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentTime: Long,
    isLoading: Boolean,
    onScreenshotClick: (Screenshot) -> Unit,
    onKeepClick: (Screenshot) -> Unit,
    onDeleteClick: (Screenshot) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = screenshots,
            key = { it.id }
        ) { screenshot ->
            ScreenshotCard(
                screenshot = screenshot,
                currentTime = currentTime,
                onClick = { onScreenshotClick(screenshot) },
                onKeepClick = { onKeepClick(screenshot) },
                onDeleteClick = { onDeleteClick(screenshot) }
            )
        }

        // Loading indicator at bottom
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Load more trigger
        item {
            LaunchedEffect(Unit) {
                onLoadMore()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotCard(
    screenshot: Screenshot,
    currentTime: Long,
    onClick: () -> Unit,
    onKeepClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current

    // Note: Previous implementation had per-card timer coroutine for countdown updates.
    // Replaced with single global timer in ViewModel for better performance.
    // If reverting, uncomment below and remove currentTime parameter:
    // var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // LaunchedEffect(screenshot.id) {
    //     while (true) {
    //         kotlinx.coroutines.delay(1000L)
    //         currentTime = System.currentTimeMillis()
    //     }
    // }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(screenshot.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.screenshot_thumbnail),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_launcher_foreground)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = screenshot.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val fileSize = File(screenshot.filePath).length()
                val sizeText = when {
                    fileSize < 1024 -> "$fileSize B"
                    fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
                    else -> "${fileSize / (1024 * 1024)} MB"
                }

                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status indicator
                when {
                    screenshot.isKept -> {
                        StatusChip(
                            text = stringResource(R.string.kept),
                            color = SuccessGreen
                        )
                    }

                    screenshot.deletionTimestamp != null -> {
                        val remaining = screenshot.deletionTimestamp!! - currentTime
                        if (remaining > 0) {
                            StatusChip(
                                text = stringResource(
                                    R.string.deletes_in_template,
                                    TimeUtils.formatTimeRemaining(remaining)
                                ),
                                color = WarningOrange
                            )
                        } else {
                            StatusChip(
                                text = stringResource(R.string.deleting),
                                color = ErrorRed
                            )
                        }
                    }

                    else -> {
                        StatusChip(
                            text = stringResource(R.string.unmarked),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                if (screenshot.deletionTimestamp != null && !screenshot.isKept) {
                    FilledIconButton(
                        onClick = onKeepClick,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SuccessGreen
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.keep_screenshot),
                            tint = Color.White
                        )
                    }
                }

                OutlinedIconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(40.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        ErrorRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_screenshot),
                        tint = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onPermissionsUpdated: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var permissionStatuses by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // Define permissions
    val readPerm =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    
    val permissions = listOfNotNull(
    "Read Screenshots" to readPerm,
    @Suppress("NewApi") "Notifications" to Manifest.permission.POST_NOTIFICATIONS,
    "Overlay Permission" to "overlay",
    "Battery Optimization" to "battery"
    ).filter { it.second != Manifest.permission.POST_NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    // Launcher for standard permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionStatuses(context, permissions) { permissionStatuses = it }
        onPermissionsUpdated?.invoke()
    }

    // Launcher for special permissions
    val specialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updatePermissionStatuses(context, permissions) { permissionStatuses = it }
        onPermissionsUpdated?.invoke()
    }

    // Update statuses
    LaunchedEffect(Unit) {
        updatePermissionStatuses(context, permissions) { permissionStatuses = it }
    }

    // Check if all permissions are already granted and auto-close if so
    LaunchedEffect(permissionStatuses) {
        if (permissionStatuses.isNotEmpty() && permissionStatuses.values.all { it }) {
            // All permissions granted, refresh monitoring status and close dialog
            onPermissionsUpdated?.invoke()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            val isOLED = MaterialTheme.colorScheme.surface == Color.Black
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f)
                    .clickable(onClick = {}) // Prevent dismiss
                    .then(
                        if (isOLED) Modifier.border(
                            1.dp,
                            Color.White,
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isOLED) Color.Black else MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isOLED) 0.dp else 8.dp)
            ) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_required),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(permissions) { (name, perm) ->
                                val isGranted = permissionStatuses[perm] ?: false
                                PermissionItem(
                                    name = name,
                                    isGranted = isGranted,
                                    onClick = {
                                        when (perm) {
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.READ_MEDIA_IMAGES,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            Manifest.permission.POST_NOTIFICATIONS -> {
                                                permissionLauncher.launch(arrayOf(perm))
                                            }

                                            "manage" -> {
                                                @Suppress("NewApi") val intent =
                                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                specialLauncher.launch(intent)
                                            }

                                            "overlay" -> {
                                                val intent =
                                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                                specialLauncher.launch(intent)
                                            }

                                            "battery" -> {
                                                @Suppress("BatteryLife") val intent =
                                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                        .setData("package:${context.packageName}".toUri())
                                                specialLauncher.launch(intent)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF1976D2) else Color(0xFFD32F2F) // Blue or Red
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

private fun updatePermissionStatuses(
    context: Context,
    permissions: List<Pair<String, String>>,
    onUpdate: (Map<String, Boolean>) -> Unit
) {
    val newStatuses = permissions.associate { (_, perm) ->
        perm to when (perm) {
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES -> ContextCompat.checkSelfPermission(
                context,
                perm
            ) == PackageManager.PERMISSION_GRANTED

            Manifest.permission.WRITE_EXTERNAL_STORAGE -> ContextCompat.checkSelfPermission(
                context,
                perm
            ) == PackageManager.PERMISSION_GRANTED

            Manifest.permission.POST_NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    perm
                ) == PackageManager.PERMISSION_GRANTED
            } else true // Assume granted for older versions
            "manage" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else true
            }

            "overlay" -> Settings.canDrawOverlays(context)
            "battery" -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }

            else -> false
        }
    }
    onUpdate(newStatuses)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.welcome_title))
        },
        text = {
            Text(text = stringResource(R.string.welcome_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.get_started))
            }
        }
    )
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    KoTheme {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyStatePreview() {
    KoTheme {
        EmptyStateScreen(tab = ScreenshotTab.ALL)
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenshotCardPreview() {
    val sampleScreenshot = Screenshot(
        filePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_20241201_123456.png",
        fileName = "Screenshot_20241201_123456.png",
        fileSize = 1024000L,
        createdAt = System.currentTimeMillis() - 3600000L, // 1 hour ago
        deletionTimestamp = System.currentTimeMillis() + 60000L, // 1 minute from now
        isKept = false,
        contentUri = "content://media/external/images/media/12345"
    )

    KoTheme {
        ScreenshotCard(
            screenshot = sampleScreenshot,
            currentTime = System.currentTimeMillis(),
            onClick = {},
            onKeepClick = {},
            onDeleteClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusChipPreview() {
    KoTheme {
        Row {
            StatusChip(text = "Marked", color = WarningOrange)
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(text = "Kept", color = SuccessGreen)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionDialogPreview() {
    KoTheme {
        PermissionDialog(onDismiss = {})
    }
}
