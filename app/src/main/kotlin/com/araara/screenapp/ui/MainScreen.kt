package com.araara.screenapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.araara.screenapp.R
import com.araara.screenapp.data.entity.Screenshot
import com.araara.screenapp.ui.theme.AppTheme
import com.araara.screenapp.ui.theme.ErrorRed
import com.araara.screenapp.ui.theme.SuccessGreen
import com.araara.screenapp.ui.theme.WarningOrange
import com.araara.screenapp.util.DebugLogger
import com.araara.screenapp.util.PermissionUtils.updatePermissionStatuses
import com.araara.screenapp.util.TimeUtils
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh screenshots when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshScreenshots()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Check if all permissions are granted to hide permission button
    var allPermissionsGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val readPerm =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        val permissions = listOfNotNull(
            readPerm,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "manage" else null,
            "overlay",
            "battery"
            // Notifications are now optional, removed from required permissions
        )

        updatePermissionStatuses(context, permissions) { statusMap ->
            allPermissionsGranted = statusMap.values.all { it }
        }
    }

    val listState = rememberLazyListState()

    // Track if user has manually scrolled away from top
    var userHasScrolled by remember { mutableStateOf(false) }

    // Detect when user scrolls away from position 0 (manual scrolling)
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex > 0) {
            userHasScrolled = true
        } else if (listState.firstVisibleItemIndex == 0) {
            // Reset the flag when user scrolls back to top
            userHasScrolled = false
        }
    }

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

    // Auto-scroll to top when tab changes - only if user hasn't scrolled and is at top
    LaunchedEffect(currentTab) {
        if (!userHasScrolled && listState.firstVisibleItemIndex == 0) {
            scope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Auto-scroll to top when new screenshots are added - simulate user scroll behavior
    LaunchedEffect(refreshTrigger) {
        if (filteredScreenshots.isNotEmpty() && !userHasScrolled) {
            scope.launch {
                // Simulate user scroll: smooth scroll to top without delay
                listState.animateScrollToItem(0)
            }
        }
    }



    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    IconButton(
                        onClick = { viewModel.refreshScreenshots() }, enabled = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }

                    IconButton(onClick = { viewModel.navigateToSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_button)
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back to top FAB (appears when scrolled)
                AnimatedVisibility(
                    visible = listState.firstVisibleItemIndex > 0,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                            // Reset scroll tracking when user uses the button
                            userHasScrolled = false
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Back to top"
                        )
                    }
                }
                // Settings FAB
                ExtendedFloatingActionButton(
                    onClick = { viewModel.navigateToSettings() },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            stringResource(R.string.settings_button)
                        )
                    },
                    text = { Text(stringResource(R.string.settings_button)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable {
                            if (!allPermissionsGranted) {
                                viewModel.showPermissionsDialog()
                            } else {
                                when (monitoringStatus) {
                                    MonitoringStatus.STOPPED -> viewModel.startMonitoring()
                                    MonitoringStatus.ACTIVE -> viewModel.stopMonitoring()
                                    MonitoringStatus.MISSING_PERMISSIONS -> {} // Should not happen if permissions granted
                                }
                            }
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
                    val refreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refreshScreenshots() },
                        state = refreshState
                    ) {
                        ScreenshotList(
                            screenshots = filteredScreenshots,
                            listState = listState,
                            currentTime = currentTime,
                            isLoading = uiState.isLoading,
                            pullRefreshState = refreshState,
                            onScreenshotClick = { viewModel.openScreenshot(it) },
                            onKeepClick = { viewModel.keepScreenshot(it) },
                            onDeleteClick = { viewModel.deleteScreenshot(it) },
                            onLoadMore = { viewModel.loadMoreScreenshots() }
                        )
                    }
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
fun LoadingScreen() {
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
fun EmptyStateScreen(tab: ScreenshotTab) {
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
fun ScreenshotList(
    screenshots: List<Screenshot>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentTime: Long,
    isLoading: Boolean,
    pullRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    onScreenshotClick: (Screenshot) -> Unit,
    onKeepClick: (Screenshot) -> Unit,
    onDeleteClick: (Screenshot) -> Unit,
    onLoadMore: () -> Unit
) {
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            onLoadMore()
        }
    }

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
                isRefreshing = isLoading,
                pullRefreshState = pullRefreshState,
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotCard(
    screenshot: Screenshot,
    currentTime: Long,
    isRefreshing: Boolean = false,
    pullRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState? = null,
    onClick: () -> Unit,
    onKeepClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val alpha = animateFloatAsState(if (isRefreshing) 0.7f else 1f).value
    val pullOffset = pullRefreshState?.distanceFraction ?: 0f
    val animatedPullOffset =
        animateFloatAsState(pullOffset * 50f).value // Adjust multiplier for effect

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
            .alpha(alpha)
            .offset(y = animatedPullOffset.dp)
            .clickable(onClick = onClick, enabled = true),
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
                    screenshot.id == -1L -> {
                        StatusChip(
                            text = stringResource(R.string.deleting),
                            color = ErrorRed
                        )
                    }

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
                if (!screenshot.isKept) {
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
fun StatusChip(
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
fun PermissionDialog(
    onDismiss: () -> Unit,
    onPermissionsUpdated: (() -> Unit)? = null,
    autoCloseWhenGranted: Boolean = true
) {
    val context = LocalContext.current
    var permissionStatuses by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var permanentlyDeniedPermissions by remember { mutableStateOf(setOf<String>()) }

    // Define permissions
    val readPerm =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    val requiredPermissions: List<Pair<String, String>> = listOfNotNull(
        "Read Screenshots" to readPerm,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "All Files Access" to "manage" else null,
        "Overlay Permission" to "overlay",
        "Battery Optimization" to "battery"
    )

    val optionalPermissions: List<Pair<String, String>> = listOfNotNull(
        @Suppress("NewApi") "Notifications" to Manifest.permission.POST_NOTIFICATIONS
    ).filter { it.second != Manifest.permission.POST_NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    // Launcher for standard permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStatuses(
            context,
            (requiredPermissions + optionalPermissions).map { it.second }) { statusMap ->
            permissionStatuses = statusMap
            // Check which permissions were denied and might be permanently denied
            results.forEach { (permission, granted) ->
                if (!granted && !statusMap[permission]!!) {
                    permanentlyDeniedPermissions = permanentlyDeniedPermissions + permission
                }
            }
        }
        onPermissionsUpdated?.invoke()
    }

    // Launcher for special permissions
    val specialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updatePermissionStatuses(
            context,
            (requiredPermissions + optionalPermissions).map { it.second }) { statusMap ->
            permissionStatuses = statusMap
        }
        onPermissionsUpdated?.invoke()
    }

    // Update statuses
    LaunchedEffect(Unit) {
        val allPermissions = (requiredPermissions + optionalPermissions).map { it.second }
        updatePermissionStatuses(
            context,
            allPermissions
        ) { statusMap -> permissionStatuses = statusMap }
    }

    // Check if all required permissions are already granted and auto-close if so (only for main screen usage)
    LaunchedEffect(permissionStatuses) {
        if (autoCloseWhenGranted && permissionStatuses.isNotEmpty()) {
            val requiredGranted = requiredPermissions.all { permissionStatuses[it.second] == true }
            if (requiredGranted) {
                // All required permissions granted, refresh monitoring status and close dialog
                onPermissionsUpdated?.invoke()
                onDismiss()
            }
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
                    .wrapContentHeight() // Natural height based on content
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
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_required),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Tab row for Required vs Optional permissions
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Required") }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Optional") }
                            )
                        }

                        val currentPermissions =
                            if (selectedTabIndex == 0) requiredPermissions else optionalPermissions

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentPermissions.forEach { (name, perm) ->
                                val isGranted = permissionStatuses[perm] ?: false
                                val isOptional = selectedTabIndex == 1 // Optional tab
                                val isPermanentlyDenied =
                                    permanentlyDeniedPermissions.contains(perm) && !isGranted
                                PermissionItem(
                                    name = name,
                                    isGranted = isGranted,
                                    isOptional = isOptional,
                                    showSettingsButton = isPermanentlyDenied,
                                    onOpenSettings = if (isPermanentlyDenied) {
                                        {
                                            val intent =
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data =
                                                        Uri.parse("package:${context.packageName}")
                                                }
                                            context.startActivity(intent)
                                        }
                                    } else null,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                                .padding(top = 4.dp, bottom = 4.dp)
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
fun PermissionItem(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    isOptional: Boolean = false,
    showSettingsButton: Boolean = false,
    onOpenSettings: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isGranted -> Color(0xFF1976D2) // Blue when granted
                isOptional && !showSettingsButton -> Color(0xFFFF9800) // Orange for optional not granted
                showSettingsButton -> Color(0xFF9C27B0) // Purple for permanently denied
                else -> Color(0xFFD32F2F) // Red for required not granted
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (showSettingsButton && onOpenSettings != null) {
                    TextButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Open Settings", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            if (showSettingsButton) {
                Text(
                    text = "Permission permanently denied. Please grant in app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

fun updatePermissionStatuses(
    context: Context,
    permissions: List<String>,
    onUpdate: (Map<String, Boolean>) -> Unit
) {
    val newStatuses = permissions.associate { perm ->
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
fun WelcomeDialog(
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
    AppTheme {
        MainScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyStatePreview() {
    AppTheme {
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

    AppTheme {
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
    AppTheme {
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
    AppTheme {
        PermissionDialog(onDismiss = {})
    }
}
