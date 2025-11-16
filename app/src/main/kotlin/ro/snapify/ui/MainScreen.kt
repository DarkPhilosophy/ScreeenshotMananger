package ro.snapify.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.snapify.BuildConfig
import ro.snapify.R
import ro.snapify.data.entity.MediaItem
import ro.snapify.data.model.FilterState
import ro.snapify.data.model.ScreenshotTab
import ro.snapify.ui.components.EmptyStateScreen
import ro.snapify.ui.components.FolderFilterBar
import ro.snapify.ui.components.LoadingBar
import ro.snapify.ui.components.LoadingScreen
import ro.snapify.ui.components.NewScreenshotDetector
import ro.snapify.ui.components.PermissionDialog
import ro.snapify.ui.components.ScreenshotCard
import ro.snapify.ui.components.ServiceStatusIndicator
import ro.snapify.ui.components.TagFilterBar
import ro.snapify.ui.components.VideoControls
import ro.snapify.ui.components.VideoPlayer
import ro.snapify.ui.components.VideoPlayerState
import ro.snapify.ui.components.rememberVideoLifecycleManager
import ro.snapify.ui.theme.AppTheme
import ro.snapify.ui.theme.ErrorRed
import ro.snapify.ui.theme.SuccessGreen
import ro.snapify.ui.theme.ThemeMode
import ro.snapify.ui.theme.WarningOrange
import ro.snapify.util.DebugLogger
import ro.snapify.util.PermissionUtils.updatePermissionStatuses

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit = {},
    preferences: ro.snapify.data.preferences.AppPreferences? = null,
    isDrawerOpen: Boolean = false
) {
    DebugLogger.info("MainScreen", "RECOMPOSING")

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = MainUiState())
    val themeMode by preferences?.themeMode?.collectAsState(initial = "system")
        ?: remember { mutableStateOf("system") }
    val language by preferences?.language?.collectAsState(initial = "en")
        ?: remember { mutableStateOf("en") }
    val currentFilterState by viewModel.currentFilterState.collectAsStateWithLifecycle(initialValue = FilterState())

    // Debug currentFilterState changes
    LaunchedEffect(currentFilterState) {
        DebugLogger.info(
            "MainScreen",
            "currentFilterState changed: folders=${currentFilterState.selectedFolders.size}, tags=${currentFilterState.selectedTags}"
        )
    }
    val mediaItems = viewModel.mediaItems
    val refreshTrigger by viewModel.refreshTrigger.collectAsStateWithLifecycle(initialValue = 0L)

    // Debug refreshTrigger changes
    LaunchedEffect(refreshTrigger) {
        DebugLogger.info("MainScreen", "RefreshTrigger changed to: $refreshTrigger")
    }
    val mediaFolderUris by viewModel.mediaFolderUris.collectAsStateWithLifecycle(initialValue = emptySet())
    val monitoringStatus by viewModel.monitoringStatus.collectAsState(initial = MonitoringStatus.STOPPED)
    val liveVideoPreviewEnabled by viewModel.liveVideoPreviewEnabled.collectAsStateWithLifecycle(
        initialValue = false
    )
    val deletingIds by viewModel.deletingIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val permanentSettingMenuEnabled by preferences?.permanentSettingMenuEnabled?.collectAsState(
        initial = false
    ) ?: remember { mutableStateOf(false) }

    // Calculate filtered item count for UI logic
    val filteredItemCount by remember(mediaItems, currentFilterState) {
        derivedStateOf {
            mediaItems.count { item ->
                // Folder filter: only include items from selected folders
                val folderMatches = currentFilterState.selectedFolders.any { selectedPath ->
                    item.filePath.lowercase().startsWith(selectedPath.lowercase())
                }

                // Tag filter: if selectedTags is empty or contains all, include all; otherwise filter by tags
                val tagMatches =
                    if (currentFilterState.selectedTags.isEmpty() || currentFilterState.isAllTagsSelected()) {
                        true
                    } else {
                        when {
                            ScreenshotTab.MARKED in currentFilterState.selectedTags && item.deletionTimestamp != null && !item.isKept -> true
                            ScreenshotTab.KEPT in currentFilterState.selectedTags && item.isKept -> true
                            ScreenshotTab.UNMARKED in currentFilterState.selectedTags && item.deletionTimestamp == null && !item.isKept -> true
                            ScreenshotTab.ALL in currentFilterState.selectedTags -> true
                            else -> false
                        }
                    }

                folderMatches && tagMatches
            }
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh screenshots and monitoring status when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            DebugLogger.info("MainScreen", "Lifecycle event: $event")
            if (event == Lifecycle.Event.ON_RESUME) {
                DebugLogger.info(
                    "MainScreen",
                    "ON_RESUME: App regained focus - refreshMediaItems DISABLED, calling refreshMonitoringStatus"
                )
                // TEMPORARILY DISABLED: viewModel.refreshMediaItems()
                viewModel.refreshMonitoringStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Video lifecycle management for multiple simultaneous videos
    rememberVideoLifecycleManager()

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

    // Available folders are the URIs themselves for display
    val availableUris = remember(mediaFolderUris) {
        mediaFolderUris.toList()
    }

    val availablePaths = remember(mediaFolderUris) {
        val parsedFolders = mediaFolderUris.mapNotNull { uri ->
            try {
                val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
                when {
                    decoded.contains("primary:") -> "/storage/emulated/0/" + decoded.substringAfter(
                        "primary:"
                    ).replace(":", "/")

                    decoded.contains("tree/") -> {
                        val parts = decoded.substringAfter("tree/").split(":")
                        if (parts.size >= 2) {
                            val path = parts.drop(1).joinToString("/")
                            "/storage/emulated/0/$path"
                        } else null
                    }

                    else -> null
                }?.removeSuffix("/")
            } catch (e: Exception) {
                null
            }
        }
        parsedFolders
    }

    // Auto-scroll to top when filter changes
    LaunchedEffect(currentFilterState) {
        scope.launch {
            listState.animateScrollToItem(0)
        }
        userHasScrolled = false // Reset scroll tracking on filter change
    }

    // Auto-scroll to top when new media items are added - simulate user scroll behavior
    LaunchedEffect(refreshTrigger) {
        if (mediaItems.isNotEmpty() && !userHasScrolled) {
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
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        isVisible = true
                    }
                    val alpha by animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        label = "titleAlpha"
                    )
                    Text(
                        text = BuildConfig.APP_DISPLAY_NAME,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        modifier = Modifier
                            .padding(start = if (isDrawerOpen) 0.dp else 60.dp)
                            .alpha(alpha)
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
                        onClick = { viewModel.refreshMediaItems() }, enabled = true
                    ) {
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
            // Right side: back to top and settings
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(20.dp)
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

                // Settings FAB (only visible when permanent setting menu is enabled)
                if (permanentSettingMenuEnabled) {
                    ExtendedFloatingActionButton(
                        onClick = { onOpenDrawer() },
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
            ServiceStatusIndicator(
                monitoringStatus = monitoringStatus,
                allPermissionsGranted = allPermissionsGranted,
                onStatusClick = {
                    when (monitoringStatus) {
                        MonitoringStatus.STOPPED -> viewModel.startMonitoring()
                        MonitoringStatus.ACTIVE -> viewModel.stopMonitoring()
                        MonitoringStatus.MISSING_PERMISSIONS -> {} // Should not happen if permissions granted
                    }
                },
                onPermissionsClick = { viewModel.showPermissionsDialog() }
            )


            // Filters (always visible)
            TagFilterBar(
                selectedTags = currentFilterState.selectedTags,
                onTagSelectionChanged = { selected ->
                    val effective = if (selected.isEmpty()) setOf(
                        ScreenshotTab.MARKED,
                        ScreenshotTab.KEPT,
                        ScreenshotTab.UNMARKED
                    ) else selected
                    viewModel.updateTagSelection(effective)
                }
            )

            FolderFilterBar(
                availableUris = availableUris,
                availablePaths = availablePaths,
                selectedPaths = currentFilterState.selectedFolders,
                onFolderSelectionChanged = { viewModel.updateFolderSelection(it) }
            )

            // Content
            when {
                uiState.isLoading && filteredItemCount == 0 -> {
                    LoadingScreen()
                }

                filteredItemCount == 0 -> {
                    EmptyStateScreen(ScreenshotTab.ALL) // TODO: Update EmptyStateScreen to handle FilterState
                }

                else -> {
                    var localLoading by remember { mutableStateOf(false) }
                    LaunchedEffect(uiState.isLoading) {
                        if (uiState.isLoading) {
                            localLoading = true
                        } else {
                            delay(
                                (200..1200).random().toLong()
                            ) // Show for 1.5 seconds after loading stops
                            localLoading = false
                        }
                    }
                    // NewScreenshotDetector with no loading to avoid UI shift
                    NewScreenshotDetector(
                        newScreenshotFlow = viewModel.newScreenshotDetected,
                        onLoadingChange = { }, // No-op to prevent loading bar
                        onNewScreenshot = {
                            scope.launch {
                                if (listState.firstVisibleItemIndex <= 1) { // At top or near top
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                    )
                    val refreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refreshMediaItems() },
                        state = refreshState
                    ) {
                        Column {
                            if (localLoading) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LoadingBar()
                            }

                            // Remember callback functions to prevent unnecessary recompositions
                            val onScreenshotClickCallback =
                                remember { { item: MediaItem -> viewModel.openMediaItem(item) } }
                            val onKeepClickCallback =
                                remember { { item: MediaItem -> viewModel.keepMediaItem(item) } }
                            val onUnkeepClickCallback =
                                remember { { item: MediaItem -> viewModel.unkeepMediaItem(item) } }
                            val onDeleteClickCallback =
                                remember { { item: MediaItem -> viewModel.deleteMediaItem(item) } }
                            val onLoadMoreCallback = remember { { viewModel.loadMoreMediaItems() } }

                            ScreenshotListComposable(
                                mediaItems = mediaItems,
                                currentFilterState = currentFilterState,
                                listState = listState,
                                isLoading = uiState.isLoading,
                                liveVideoPreviewEnabled = liveVideoPreviewEnabled,
                                deletingIds = deletingIds,
                                onScreenshotClick = onScreenshotClickCallback,
                                onKeepClick = onKeepClickCallback,
                                onUnkeepClick = onUnkeepClickCallback,
                                onDeleteClick = onDeleteClickCallback,
                                onLoadMore = onLoadMoreCallback
                            )
                        }
                    }
                }
            }
        }
    }


    // Theme dialog
    if (uiState.showThemeDialog) {
        ThemeDialog(
            currentTheme = themeMode,
            onThemeSelected = { newTheme ->
                scope.launch {
                    preferences?.setThemeMode(newTheme)
                }
                viewModel.hideThemeDialog()
            },
            onDismiss = { viewModel.hideThemeDialog() }
        )
    }

    // Language dialog
    if (uiState.showLanguageDialog) {
        LanguageDialog(
            currentLanguage = language,
            onLanguageSelected = { newLang ->
                scope.launch {
                    preferences?.setLanguage(newLang)
                }
                viewModel.hideLanguageDialog()
            },
            onDismiss = { viewModel.hideLanguageDialog() }
        )
    }

    // Operation Mode dialog
    if (uiState.showOperationModeDialog) {
        OperationModeDialog(
            onDismiss = { viewModel.hideOperationModeDialog() }
        )
    }

    // Deletion Time dialog
    if (uiState.showDeletionTimeDialog) {
        DeletionTimeDialog(
            onDismiss = { viewModel.hideDeletionTimeDialog() }
        )
    }

    // Welcome dialog for first launch
    if (uiState.showWelcomeDialog) {
        WelcomeDialog(
            onDismiss = { viewModel.dismissWelcomeDialog() }
        )
    }


    // Video preview dialog
    uiState.videoPreviewItem?.let { mediaItem ->
        VideoPreviewDialog(
            mediaItem = mediaItem,
            onDismiss = { viewModel.closeVideoPreview() }
        )
    }

    // Image preview dialog
    uiState.imagePreviewItem?.let { mediaItem ->
        ImagePreviewDialog(
            mediaItem = mediaItem,
            onDismiss = { viewModel.closeImagePreview() }
        )
    }
}


@Composable
fun PermissionDialogOld(
    context: Context,
    onDismiss: () -> Unit,
    onPermissionsUpdated: (() -> Unit)? = null,
    autoCloseWhenGranted: Boolean = false
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var permissionStatuses by remember { mutableStateOf(mapOf<String, Boolean>()) }
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

    // Video lifecycle management
    val videoLifecycleManager = ro.snapify.ui.components.rememberVideoLifecycleManager()

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
fun ThemeDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text(stringResource(R.string.choose_theme))
        },
        text = {
            Column {
                listOf(
                    "light" to stringResource(R.string.light),
                    "dark" to stringResource(R.string.dark),
                    "system" to stringResource(R.string.system_default),
                    "oled" to stringResource(R.string.oled_theme)
                ).forEach { (theme, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text(stringResource(R.string.choose_language))
        },
        text = {
            Column {
                listOf(
                    "en" to stringResource(R.string.english),
                    "ro" to stringResource(R.string.romanian)
                ).forEach { (lang, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(lang) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == lang,
                            onClick = { onLanguageSelected(lang) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationModeDialog(
    onDismiss: () -> Unit
) {
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text("Operation Mode")
        },
        text = {
            Column {
                listOf(
                    "manual" to "Manual",
                    "automatic" to "Automatic"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == "manual",
                            onClick = { onDismiss() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletionTimeDialog(
    onDismiss: () -> Unit
) {
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text("Deletion Time")
        },
        text = {
            Column {
                listOf(
                    "5min" to "5 minutes",
                    "15min" to "15 minutes",
                    "1h" to "1 hour",
                    "1d" to "1 day",
                    "1w" to "1 week"
                ).forEach { (time, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = time == "1h",
                            onClick = { onDismiss() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    currentFilter: ScreenshotTab,
    onFilterSelected: (ScreenshotTab) -> Unit,
    onDismiss: () -> Unit
) {
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text("Filter Screenshots")
        },
        text = {
            Column {
                listOf(
                    ScreenshotTab.ALL to "All",
                    ScreenshotTab.MARKED to "Marked for Deletion",
                    ScreenshotTab.KEPT to "Kept",
                    ScreenshotTab.UNMARKED to "Unmarked"
                ).forEach { (tab, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFilterSelected(tab) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == tab,
                            onClick = { onFilterSelected(tab) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewDialog(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    var isFullscreen by remember { mutableStateOf(false) } // No auto fullscreen
    var controlsVisible by remember { mutableStateOf(!isFullscreen) } // Start hidden in fullscreen
    var hideControlsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var videoState by remember { mutableStateOf<VideoPlayerState?>(null) }

    // Auto-hide controls timer
    val scope = rememberCoroutineScope()
    fun resetHideTimer() {
        hideControlsJob?.cancel()
        if (videoState?.isPlaying == true) {
            hideControlsJob = scope.launch {
                delay(3000)
                controlsVisible = false
            }
        }
    }

    // Handle orientation changes (no forced fullscreen)
    LaunchedEffect(isLandscape) {
        // UI visibility logic applies to both orientations now
        controlsVisible = videoState?.isPlaying != true
        if (videoState?.isPlaying == true) {
            resetHideTimer()
        } else {
            hideControlsJob?.cancel()
        }
    }

    // Sync UI visibility with play state (same logic for both orientations)
    LaunchedEffect(videoState?.isPlaying) {
        videoState?.let { state ->
            if (state.isPlaying) {
                // Auto-hide UI when playing (in any orientation)
                controlsVisible = false
                resetHideTimer()
            } else {
                // Show UI when paused
                controlsVisible = true
                hideControlsJob?.cancel()
            }
        }
    }

    // Pause video when dialog loses focus
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                videoState?.exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = {
            // Restore orientation when closing
            val activity = context as? android.app.Activity
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = !isLandscape // Full screen in landscape
        )
    ) {
        Card(
            modifier = Modifier
                .then(
                    if (isLandscape) Modifier.fillMaxSize() else Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = configuration.screenHeightDp.dp * 0.8f)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            // Video player
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        // Toggle UI visibility on video click
                        controlsVisible = !controlsVisible
                        // Cancel any pending hide timer
                        hideControlsJob?.cancel()
                        // If controls are now visible and video is playing, start hide timer
                        if (controlsVisible && videoState?.isPlaying == true) {
                            resetHideTimer()
                        }
                    }) {
                videoState = VideoPlayer(
                    mediaItem = mediaItem,
                    modifier = Modifier.fillMaxSize(), // Fill the Card container
                    onEndReached = { player ->
                        // Loop video
                        player.seekTo(0)
                        player.play()
                    }
                )


                // Top bar overlay
                if (controlsVisible) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mediaItem.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Touch overlay to show controls
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .pointerInput(Unit) {
//                                awaitPointerEventScope {
//                                    while (true) {
//                                        awaitPointerEvent()
//                                        resetHideTimer()
//                                    }
//                                }
//                            }
//                    )

                // Control bar overlay
                if (controlsVisible && videoState != null) {
                    VideoControls(
                        isPlaying = videoState!!.isPlaying,
                        position = videoState!!.position,
                        duration = videoState!!.duration,
                        onPlayPause = {
                            videoState!!.exoPlayer?.let { player ->
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        },
                        onRewind = {
                            val newPos = (videoState!!.position - 0.1f).coerceAtLeast(0f)
                            videoState!!.exoPlayer?.seekTo((newPos * videoState!!.duration).toLong())
                        },
                        onForward = {
                            val newPos = (videoState!!.position + 0.1f).coerceAtMost(1f)
                            videoState!!.exoPlayer?.seekTo((newPos * videoState!!.duration).toLong())
                        },
                        onSeek = { newPosition ->
                            videoState!!.exoPlayer?.seekTo((newPosition * videoState!!.duration).toLong())
                        },
                        onFullscreen = {
                            isFullscreen = !isFullscreen
                            val activity = context as? android.app.Activity
                            if (isFullscreen) {
                                activity?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                // Hide system UI for immersive fullscreen
                                activity?.window?.let { window ->
                                    androidx.core.view.WindowCompat.getInsetsController(
                                        window,
                                        window.decorView
                                    ).apply {
                                        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                        systemBarsBehavior =
                                            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    }
                                }
                            } else {
                                // Force portrait on exit to ensure proper orientation reset
                                activity?.requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                // Restore system UI
                                activity?.window?.let { window ->
                                    androidx.core.view.WindowCompat.getInsetsController(
                                        window,
                                        window.decorView
                                    ).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreviewDialog(
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var uiVisible by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) (configuration.screenHeightDp * 0.8f).dp else (configuration.screenHeightDp * 0.9f).dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        // Toggle UI visibility on image click
                        uiVisible = !uiVisible
                    }) {
                // Image with controls and overlays
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaItem.contentUri ?: "file://${mediaItem.filePath}")
                            .build(),
                        contentDescription = mediaItem.fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation
                            ),
                        contentScale = ContentScale.Fit
                    )

                    // Title overlay
                    if (uiVisible) {
                        Text(
                            text = mediaItem.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Close button overlay
                    if (uiVisible) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    RoundedCornerShape(50)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Control buttons overlay
                    if (uiVisible) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scale = (scale / 1.2f).coerceAtLeast(0.5f) },
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom out",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { scale = (scale * 1.2f).coerceAtMost(3f) },
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom in",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { rotation = (rotation + 90f) % 360f },
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
    val sampleScreenshot = MediaItem(
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
            onClick = {},
            onKeepClick = {},
            onUnkeepClick = {},
            onDeleteClick = {}
        )
    }
}


@Composable
fun DuoDrawer(
    isOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    menuContent: @Composable (Boolean) -> Unit,
    content: @Composable () -> Unit,
    dialogContent: @Composable () -> Unit = {},
    showDialog: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val drawerOffset by animateDpAsState(
        targetValue = when {
            !isOpen -> -screenWidth
            isOpen && !showDialog -> 0.dp
            isOpen && showDialog -> screenWidth / 2
            else -> -screenWidth
        },
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        content()

        // Semi-transparent overlay when drawer is open
        if (isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Drawer sliding from left
        Box(
            modifier = Modifier
                .fillMaxSize() // Full width drawer
                .offset(x = drawerOffset)
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 32.dp) // Account for status bar
        ) {
            menuContent(isOpen)
        }
    }

    dialogContent()
}

@Composable
internal fun AnimatedHamburgerMenu(
    isOpen: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 90f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic)
    )

    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = if (isOpen) "Close drawer" else "Open drawer",
            modifier = Modifier.graphicsLayer(rotationZ = rotation)
        )
    }
}

internal fun LazyListScope.SettingsContent(
    settingsViewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    folderPicker: ManagedActivityResultLauncher<Uri?, Uri?>,
    permissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    developerUnlocked: Boolean,
    onDeveloperUnlocked: () -> Unit,
    onDeveloperLocked: () -> Unit,
    onOpenPermissions: () -> Unit = {},
    onShowFolderDialog: () -> Unit = {},
    onNavigateToConsole: () -> Unit = {},
    onThemeChangeCallback: (String) -> Unit = {}
) {
    // Interface & Appearance
    item {
        SettingsSectionHeader("Interface & Appearance")
    }

    item {
        ThemeSelector(
            currentTheme = settingsViewModel.currentTheme.collectAsState(initial = ThemeMode.SYSTEM).value,
            onThemeSelected = { theme ->
                scope.launch {
                    settingsViewModel.setThemeMode(theme)
                }
            },
            onThemeChange = { /* callback if needed */ }
        )
    }

    item {
        LanguageSelector(
            currentLanguage = settingsViewModel.language.collectAsState(initial = "en").value,
            onLanguageSelected = { lang ->
                scope.launch {
                    settingsViewModel.setLanguage(lang)
                }
            }
        )
    }

    // Permissions
    item {
        SettingsSectionHeader("Permissions")
    }

    item {
        PermissionsSection(onOpenPermissions = onOpenPermissions)
    }

    // Screenshot Management
    item {
        SettingsSectionHeader("Screenshot Management")
    }

    item {
        ModeSelector(
            isManualMode = settingsViewModel.isManualMode.collectAsState(initial = false).value,
            onModeChanged = { manual ->
                scope.launch {
                    settingsViewModel.setManualMode(manual)
                }
            },
            currentTime = settingsViewModel.deletionTime.collectAsState(initial = 15 * 60 * 1000L).value,
            onTimeSelected = { time ->
                scope.launch {
                    settingsViewModel.setDeletionTime(time)
                }
            }
        )
    }

    item {
        NotificationToggle(
            enabled = settingsViewModel.notificationsEnabled.collectAsState(initial = true).value,
            onToggle = { enabled ->
                scope.launch {
                    settingsViewModel.setNotificationsEnabled(enabled)
                }
            },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        )
    }

    // Storage
    item {
        SettingsSectionHeader("Storage")
    }

    item {
        val folderUris =
            settingsViewModel.mediaFolderUris.collectAsState(initial = emptySet()).value
        val folderCount = folderUris.size
        val summaryText = when {
            folderCount == 0 -> "No folders configured"
            folderCount == 1 -> {
                val uri = folderUris.first()
                val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
                val folderName = when {
                    decoded.contains("primary:") -> "Primary:" + decoded.substringAfter("primary:")
                    decoded.contains("tree/") -> "Tree:" + decoded.substringAfter("tree/")
                    else -> uri
                }
                folderName
            }

            else -> "$folderCount folders selected"
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowFolderDialog)
        ) {
            ListItem(
                headlineContent = { Text("Media Folders") },
                supportingContent = { Text(summaryText) },
                trailingContent = {
                    Icon(Icons.Default.Folder, contentDescription = "Manage folders")
                }
            )
        }
    }

    // Experimental Features
    item {
        SettingsSectionHeader("Experimental Features")
    }

    item {
        AutoCleanupToggle(
            enabled = settingsViewModel.autoCleanupEnabled.collectAsState(initial = false).value,
            onToggle = { enabled ->
                scope.launch {
                    settingsViewModel.setAutoCleanupEnabled(enabled)
                }
            }
        )
    }

    item {
        LiveVideoPreviewToggle(
            enabled = settingsViewModel.liveVideoPreviewEnabled.collectAsState(initial = false).value,
            onToggle = { enabled ->
                scope.launch {
                    settingsViewModel.setLiveVideoPreviewEnabled(enabled)
                }
            }
        )
    }

    item {
        PermanentSettingMenuToggle(
            enabled = settingsViewModel.permanentSettingMenuEnabled.collectAsState(initial = false).value,
            onToggle = { enabled ->
                scope.launch {
                    settingsViewModel.setPermanentSettingMenuEnabled(enabled)
                }
            }
        )
    }

    // Developer options
    if (developerUnlocked) {
        item {
            CrashTestButton()
        }

        item {
            ConsoleButton(onNavigateToConsole = onNavigateToConsole)
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Turn off developer mode") },
                    supportingContent = {
                        Text(
                            "Disable developer options and features.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        OutlinedButton(onClick = onDeveloperLocked) {
                            Text("Turn Off")
                        }
                    }
                )
            }
        }
    }

    // Information Section
    item {
        SettingsSectionHeader(stringResource(R.string.information_section))
    }

    item {
        VersionInfo(
            isDeveloperMode = developerUnlocked,
            snackbarHostState = snackbarHostState,
            onActivateDeveloperMode = onDeveloperUnlocked
        )
    }

    item {
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuContent(
    isOpen: Boolean,
    onHomeClick: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
    isDrawerOpen: Boolean = false,
    preferences: ro.snapify.data.preferences.AppPreferences? = null,
    mainViewModel: MainViewModel = hiltViewModel(),
    dialogContent: @Composable () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val developerUnlocked by settingsViewModel.developerModeEnabled.collectAsState(initial = false)
    var pressCount by remember { mutableStateOf(0) }

    // Folder picker launcher
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            // Take persistable permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // Save the URI
            scope.launch {
                settingsViewModel.addMediaFolder(it.toString())
            }
        }
    }

    // Permission launcher for notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Update permission status for UI
        // The NotificationToggle will check the actual permission
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // Settings Content
            SettingsContent(
                settingsViewModel = settingsViewModel,
                scope = scope,
                snackbarHostState = snackbarHostState,
                folderPicker = folderPicker,
                permissionLauncher = permissionLauncher,
                developerUnlocked = developerUnlocked,
                onDeveloperUnlocked = { settingsViewModel.setDeveloperModeEnabled(true) },
                onDeveloperLocked = { settingsViewModel.setDeveloperModeEnabled(false) },
                onOpenPermissions = { showPermissionDialog = true },
                onShowFolderDialog = { showFolderDialog = true },
                onNavigateToConsole = {
                    context.startActivity(Intent(context, DebugConsoleActivity::class.java))
                }
            )



            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showFolderDialog) {
        FolderManagementDialog(
            mediaFolderUris = settingsViewModel.mediaFolderUris.collectAsState(initial = emptySet()).value,
            onAddFolder = { folderPicker.launch(null) },
            onAddUri = { settingsViewModel.addMediaFolder(it) },
            onRemoveFolder = { settingsViewModel.removeMediaFolder(it) },
            onDismiss = { showFolderDialog = false }
        )
    }

    if (showPermissionDialog) {
        PermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onPermissionsUpdated = {
                // Start the service when permissions are granted
                val intent = android.content.Intent(
                    context,
                    ro.snapify.service.ScreenshotMonitorService::class.java
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            autoCloseWhenGranted = false
        )
    }

    dialogContent()
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FolderManagementDialog(
    mediaFolderUris: Set<String>,
    onAddFolder: () -> Unit,
    onAddUri: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {


    // Create a stable list of URI-path pairs, sorted by formatted path for consistent display
    val folderItems = remember(mediaFolderUris) {
        mediaFolderUris.map { uri ->
            Pair(uri, uri) // Simple mapping for now: display URI as-is
        }.sortedBy { it.first }
    }

    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = if (isOLED) Modifier.border(
            1.dp,
            Color.White,
            RoundedCornerShape(28.dp)
        ) else Modifier,
        title = {
            Text(
                "Manage Media Folders",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            androidx.compose.material3.MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                Column {
                    if (mediaFolderUris.isEmpty()) {
                        Text(
                            "No folders selected.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            "Selected folders:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        ) {
                            folderItems.forEach { item ->
                                val (uri, _) = item
                                item {
                                    val formattedPath = try {
                                        if (uri.isEmpty()) {
                                            "Default (Pictures/Screenshots)"
                                        } else {
                                            val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
                                            when {
                                                decoded.contains("primary:") -> "Primary:" + decoded.substringAfter(
                                                    "primary:"
                                                )
                                                    .replace("%2F", "/").replace("%3A", ":")

                                                decoded.contains("tree/") -> {
                                                    val parts =
                                                        decoded.substringAfter("tree/").split(":")
                                                    if (parts.size >= 2) {
                                                        val volume = parts[0]
                                                        val path = parts[1].replace("%2F", "/")
                                                            .replace("%3A", ":")
                                                        "$volume:$path"
                                                    } else decoded
                                                }

                                                else -> decoded
                                            }
                                        }
                                    } catch (e: Exception) {
                                        "Invalid folder path"
                                    }

                                    MediaFolderItem(
                                        path = formattedPath,
                                        onRemove = { onRemoveFolder(uri) },
                                        showDragHandle = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onAddFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Folder")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun MediaFolderItem(
    path: String,
    onRemove: () -> Unit,
    showDragHandle: Boolean = false
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDragHandle) {
                Icon(
                    Icons.Default.Folder, // Could use a drag handle icon, but using folder for now
                    contentDescription = "Drag handle",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove folder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedScreenshotCard(
    screenshot: MediaItem,
    isVisible: Boolean,
    isRefreshing: Boolean,
    liveVideoPreviewEnabled: Boolean,
    onClick: () -> Unit,
    onKeepClick: () -> Unit,
    onUnkeepClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    key(screenshot.id) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(1000)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(1000)
            ),
            exit = fadeOut(animationSpec = tween(500)) + scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(500)
            )
        ) {
            ScreenshotCard(
                screenshot = screenshot,
                isRefreshing = isRefreshing,
                liveVideoPreviewEnabled = liveVideoPreviewEnabled,
                onClick = onClick,
                onKeepClick = onKeepClick,
                onUnkeepClick = onUnkeepClick,
                onDeleteClick = onDeleteClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotListComposable(
    mediaItems: SnapshotStateList<MediaItem>,
    currentFilterState: ro.snapify.data.model.FilterState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    liveVideoPreviewEnabled: Boolean,
    deletingIds: Set<Long>,
    onScreenshotClick: (MediaItem) -> Unit,
    onKeepClick: (MediaItem) -> Unit,
    onUnkeepClick: (MediaItem) -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredMediaItems by remember(mediaItems, currentFilterState) {
        DebugLogger.info(
            "ScreenshotListComposable",
            "Recalculating filteredMediaItems: mediaItems.size=${mediaItems.size}"
        )
        derivedStateOf {
            val result = mediaItems.filter { item ->
                // Folder filter: only include items from selected folders
                val folderMatches = currentFilterState.selectedFolders.any { selectedPath ->
                    item.filePath.lowercase().startsWith(selectedPath.lowercase())
                }

                // Tag filter: if selectedTags is empty or contains all, include all; otherwise filter by tags
                val tagMatches =
                    if (currentFilterState.selectedTags.isEmpty() || currentFilterState.isAllTagsSelected()) {
                        true
                    } else {
                        when {
                            ScreenshotTab.MARKED in currentFilterState.selectedTags && item.deletionTimestamp != null && !item.isKept -> true
                            ScreenshotTab.KEPT in currentFilterState.selectedTags && item.isKept -> true
                            ScreenshotTab.UNMARKED in currentFilterState.selectedTags && item.deletionTimestamp == null && !item.isKept -> true
                            ScreenshotTab.ALL in currentFilterState.selectedTags -> true
                            else -> false
                        }
                    }

                folderMatches && tagMatches
            }
            DebugLogger.info("ScreenshotListComposable", "Filtered to ${result.size} items")
            result
        }
    }

    DebugLogger.info(
        "ScreenshotListComposable",
        "RECOMPOSING: filteredItems=${filteredMediaItems.size}"
    )

    // Content
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
            count = filteredMediaItems.size,
            key = { index -> filteredMediaItems[index].id }
        ) { index ->
            val screenshot = filteredMediaItems[index]

            // Remember callbacks to prevent unnecessary recompositions
            val onClickCallback = remember(screenshot.id) { { onScreenshotClick(screenshot) } }
            val onKeepCallback = remember(screenshot.id) { { onKeepClick(screenshot) } }
            val onUnkeepCallback = remember(screenshot.id) { { onUnkeepClick(screenshot) } }
            val onDeleteCallback = remember(screenshot.id) { { onDeleteClick(screenshot) } }

            AnimatedScreenshotCard(
                screenshot = screenshot,
                isVisible = screenshot.id !in deletingIds,
                isRefreshing = isLoading,
                liveVideoPreviewEnabled = liveVideoPreviewEnabled,
                onClick = onClickCallback,
                onKeepClick = onKeepCallback,
                onUnkeepClick = onUnkeepCallback,
                onDeleteClick = onDeleteCallback
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

@Preview(showBackground = true)
@Composable
fun PermissionDialogPreview() {
    AppTheme {
        PermissionDialog(onDismiss = {})
    }
}
