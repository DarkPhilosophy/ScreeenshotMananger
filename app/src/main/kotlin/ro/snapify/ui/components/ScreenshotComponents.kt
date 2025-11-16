package ro.snapify.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.snapify.R
import ro.snapify.data.entity.MediaItem
import ro.snapify.data.model.ScreenshotTab
import ro.snapify.ui.MonitoringStatus
import ro.snapify.ui.theme.ErrorRed
import ro.snapify.ui.theme.SuccessGreen
import ro.snapify.ui.theme.WarningOrange
import ro.snapify.util.DebugLogger
import ro.snapify.util.TimeUtils
import java.io.File

// Multi-video player manager with memory limits - supports limited simultaneous videos
class MultiVideoManager {
    companion object {
        const val MAX_SLOTS = 20
    }

    private val activePlayers = mutableMapOf<String, ExoPlayer>()
    private val videoContexts = mutableMapOf<String, android.content.Context>()
    private val videoCreationTimes = mutableMapOf<String, Long>()


    fun getOrCreatePlayerForVideo(
        context: android.content.Context,
        videoId: String,
        videoUri: android.net.Uri
    ): ExoPlayer? {
        return try {
            // Check if we already have a player for this video
            val existingPlayer = activePlayers[videoId]
            if (existingPlayer != null && videoContexts[videoId] == context) {
                DebugLogger.info("VideoLifecycle", "Reused existing player for $videoId")
                return existingPlayer
            }


            // Create new player for this video with reduced buffer sizes to prevent OOM
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    500,
                    1000,
                    250,
                    500
                ) // min 0.5s, max 1s, playback after rebuffer 0.25s, rebuffer 0.5s
                .setTargetBufferBytes(256 * 1024) // 256KB target buffer
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
                    setMediaItem(mediaItem)
                    prepare()
                    volume = 0f // Mute
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                    playWhenReady = true // Start playing immediately
                }

            // Store the player
            activePlayers[videoId] = player
            videoContexts[videoId] = context
            videoCreationTimes[videoId] = System.currentTimeMillis()

            DebugLogger.info(
                "VideoLifecycle",
                "Created NEW player for $videoId, active players: ${activePlayers.size}"
            )

            player
        } catch (e: Exception) {
            DebugLogger.error(
                "VideoLifecycle",
                "Failed to create player for $videoId: ${e.message}"
            )
            null
        }
    }

    private fun cleanupLeastRecentlyUsed() {
        // Find the oldest player to remove (LRU strategy)
        val oldestVideoId = videoCreationTimes.minByOrNull { it.value }?.key
        oldestVideoId?.let { videoId ->
            activePlayers[videoId]?.apply {
                pause()
                release()
            }
            activePlayers.remove(videoId)
            videoContexts.remove(videoId)
            videoCreationTimes.remove(videoId)
        }
    }

    fun pauseAll() {
        activePlayers.values.forEach { it.pause() }
    }

    fun resumeAll() {
        activePlayers.values.forEach { it.playWhenReady = true }
    }


    fun getPlayerForVideo(videoId: String): ExoPlayer? = activePlayers[videoId]

    fun isVideoPlaying(videoId: String): Boolean {
        return activePlayers[videoId]?.isPlaying == true
    }

    fun getActiveVideoIds(): Set<String> {
        return activePlayers.keys
    }

    fun cleanupInvisibleVideos() {
        // Get videos that are no longer visible (not in the scoring map)
        val videosToRemove = activePlayers.keys.filter { it !in visibleVideosWithScores }
        if (videosToRemove.isNotEmpty()) {
            // DebugLogger.debug("VideoLifecycle", "Cleaning up invisible videos: $videosToRemove")
        }

        videosToRemove.forEach { videoId ->
            activePlayers[videoId]?.apply {
                pause()
                release()
            }
            activePlayers.remove(videoId)
            videoContexts.remove(videoId)
            videoCreationTimes.remove(videoId)
        }
    }

    fun releaseVideo(videoId: String) {
        activePlayers[videoId]?.apply {
            pause()
            release()
        }
        activePlayers.remove(videoId)
        videoContexts.remove(videoId)
        videoCreationTimes.remove(videoId)
        DebugLogger.info("VideoLifecycle", "RELEASED and removed player for $videoId")
    }

    fun release() {
        activePlayers.values.forEach { player ->
            try {
                player.release()
            } catch (e: Exception) {
            }
        }
        activePlayers.clear()
        videoContexts.clear()
        videoCreationTimes.clear()
    }
}

// Global multi-video manager - supports multiple simultaneous videos
private val globalVideoManager = MultiVideoManager()

// Track currently visible videos with their visibility scores
private val visibleVideosWithScores = mutableMapOf<String, Float>()

// Track which videos were recently started to prevent immediate stopping
private val recentlyStartedVideos = mutableSetOf<String>()

// Debounce job for updateActivePlayers
private var updateJob: kotlinx.coroutines.Job? = null

// Helper functions for video and file utilities
private fun isVideoFile(filePath: String): Boolean {
    return filePath.lowercase().let {
        it.endsWith(".mp4") || it.endsWith(".avi") || it.endsWith(".mov") || it.endsWith(".mkv") || it.endsWith(
            ".webm"
        )
    }
}

private fun getFileSizeText(filePath: String): String {
    val fileSize = File(filePath).length()
    return when {
        fileSize < 1024 -> "$fileSize B"
        fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
        else -> "${fileSize / (1024 * 1024)} MB"
    }
}

private suspend fun loadVideoThumbnail(
    context: android.content.Context,
    screenshot: MediaItem
): Bitmap? {
    val uri = screenshot.contentUri?.let { android.net.Uri.parse(it) } ?: android.net.Uri.fromFile(
        File(screenshot.filePath)
    )
    return getVideoThumbnail(context, uri)
}

private fun calculateVisibilityScore(
    bounds: androidx.compose.ui.geometry.Rect,
    screenHeight: Float
): Float {
    val visibleHeight = maxOf(0f, minOf(bounds.bottom, screenHeight) - maxOf(bounds.top, 0f))
    val totalHeight = bounds.bottom - bounds.top
    return if (totalHeight > 0) visibleHeight / totalHeight else 0f
}

// Debounced update which videos should be active based on visibility scores
private fun updateActivePlayers() {
    updateJob?.cancel()
    updateJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        kotlinx.coroutines.delay(50) // 50ms debounce
        doUpdateActivePlayers()
    }
}

// Actual update logic
private fun doUpdateActivePlayers() {
    // Get top visible videos limited by max slots, sorted by score desc, then by id asc
    val topVisibleVideos = visibleVideosWithScores
        .entries
        .sortedWith { a, b ->
            val scoreCmp = b.value.compareTo(a.value) // desc
            if (scoreCmp != 0) scoreCmp else a.key.compareTo(b.key) // asc by id
        }
        .take(MultiVideoManager.MAX_SLOTS)
        .map { it.key }
        .toSet()


    // Get current active players
    val currentActiveVideos = globalVideoManager.getActiveVideoIds()

    // Videos that should start playing (visible but not currently active)
    val videosToStart = topVisibleVideos - currentActiveVideos

    // Videos that should stop playing (active but not in top visible)
    val videosToStop = currentActiveVideos - topVisibleVideos

    if (videosToStart.isNotEmpty() || videosToStop.isNotEmpty()) {
        DebugLogger.debug(
            "VideoUpdate",
            "Current active: $currentActiveVideos, To START: $videosToStart, To STOP: $videosToStop"
        )
    }

    // Player operations must be on main thread
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        // Stop and release videos that are no longer in top visible
        videosToStop.forEach { videoId ->
            globalVideoManager.releaseVideo(videoId)
            DebugLogger.info("VideoUpdate", "RELEASED video: $videoId")
        }

        // Start new videos - create new players
        videosToStart.forEach { videoId ->
            // Player will be created in VisibilityAwareVideo when needed
            recentlyStartedVideos.add(videoId)
            DebugLogger.info("VideoUpdate", "MARKED for start: $videoId")

            // Remove from recently started after delay
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(5000) // Grace period
                recentlyStartedVideos.remove(videoId)
                DebugLogger.info("VideoUpdate", "REMOVED from recently started: $videoId")
            }
        }

        // Clean up any videos that are no longer visible at all
        globalVideoManager.cleanupInvisibleVideos()
        if (videosToStart.isNotEmpty() || videosToStop.isNotEmpty()) {
            DebugLogger.debug(
                "VideoUpdate",
                "Cleanup done, active players: ${globalVideoManager.getActiveVideoIds().size}"
            )
        }
    }
}

// Composable to handle video visibility and playback management for multiple videos
@Composable
fun VisibilityAwareVideo(
    videoId: String,
    videoUri: android.net.Uri,
    modifier: Modifier = Modifier,
    content: @Composable (ExoPlayer?, Boolean) -> Unit
) {
    val context = LocalContext.current
    var isPlayerCreated by remember { mutableStateOf(false) }
    var currentIsPlaying by remember { mutableStateOf(false) }


    // Listen to player state changes to update playing status
    val currentPlayer = remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(currentPlayer.value) {
        val p = currentPlayer.value
        var listener: androidx.media3.common.Player.Listener? = null
        if (p != null) {
            listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    currentIsPlaying = isPlaying
                    DebugLogger.info("VideoPlayer", "Is playing changed for $videoId: $isPlaying")
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateStr = when (playbackState) {
                        androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                        androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                        androidx.media3.common.Player.STATE_READY -> "READY"
                        androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    DebugLogger.info("VideoPlayer", "Playback state for $videoId: $stateStr")
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    DebugLogger.error("VideoPlayer", "Player error for $videoId: ${error.message}")
                }
            }
            p.addListener(listener)
            // Set initial state in case player is already playing
            currentIsPlaying = p.isPlaying
            DebugLogger.info(
                "VideoPlayer",
                "Initial state for $videoId: playing=${p.isPlaying}, state=${p.playbackState}"
            )
        } else {
            currentIsPlaying = false
        }
        onDispose {
            val p2 = currentPlayer.value
            if (p2 != null && listener != null) {
                p2.removeListener(listener)
            }
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            // DebugLogger.debug("VideoLifecycle", "Disposing VisibilityAwareVideo for $videoId")
            // Remove from visible videos when disposed
            visibleVideosWithScores.remove(videoId)
            globalVideoManager.releaseVideo(videoId)
            updateActivePlayers()
            isPlayerCreated = false
            currentIsPlaying = false
        }
    }

    Box(modifier = modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val screenHeight =
            android.content.res.Resources.getSystem().displayMetrics.heightPixels.toFloat()

        // Calculate visibility score (0.0 to 1.0)
        val visibilityScore = calculateVisibilityScore(bounds, screenHeight)

        if (visibilityScore > 0f) { // Any pixel visible
            // Video is at least partially visible - include in priority list
            visibleVideosWithScores[videoId] = visibilityScore
            // DebugLogger.debug("VideoLifecycle", "Video $videoId ENTER visible with score $visibilityScore")
        } else {
            // Video is completely off screen - remove from priority list
            visibleVideosWithScores.remove(videoId)
            // DebugLogger.debug("VideoLifecycle", "Video $videoId EXIT not visible")
        }

        // Update which videos should be active based on scores
        updateActivePlayers()

        // Calculate current top visible videos
        val currentTopVisible = visibleVideosWithScores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Float>> { it.value }.thenBy { it.key })
            .take(MultiVideoManager.MAX_SLOTS)
            .map { it.key }
            .toSet()

        if (visibilityScore > 0f) {
            // Create player if not already created, in top visible, and not recently started
            if (!isPlayerCreated && videoId in currentTopVisible && videoId !in recentlyStartedVideos) {
                val createdPlayer =
                    globalVideoManager.getOrCreatePlayerForVideo(context, videoId, videoUri)
                isPlayerCreated = true
                currentPlayer.value = createdPlayer
                // DebugLogger.debug("VideoLifecycle", "Created player for $videoId: ${player != null}")
            }
        }

        // Update the player state
        currentPlayer.value = globalVideoManager.getPlayerForVideo(videoId)
    }) {
        // Check if this video is currently one of the active playing videos
        val topVisibleVideos = visibleVideosWithScores
            .entries
            .sortedWith { a, b ->
                val scoreCmp = b.value.compareTo(a.value) // desc
                if (scoreCmp != 0) scoreCmp else a.key.compareTo(b.key) // asc by id
            }
            .take(MultiVideoManager.MAX_SLOTS)
            .map { it.key }
            .toSet()

        val isCurrentlyPlaying = videoId in topVisibleVideos && currentIsPlaying

        content(currentPlayer.value, isCurrentlyPlaying)
    }
}

// Lifecycle management for multi-video playback
@Composable
fun rememberVideoLifecycleManager(): androidx.lifecycle.LifecycleObserver {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    return remember {
        androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    globalVideoManager.pauseAll()
                }

                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    globalVideoManager.resumeAll()
                }

                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    globalVideoManager.release()
                }

                else -> {}
            }
        }.also { observer ->
            lifecycleOwner.lifecycle.addObserver(observer)
        }
    }
}

/**
 * Service status indicator showing monitoring status with animated visibility
 */
@Composable
fun ServiceStatusIndicator(
    monitoringStatus: MonitoringStatus,
    allPermissionsGranted: Boolean,
    onStatusClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier.zIndex(1f)
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
                        onPermissionsClick()
                    } else {
                        onStatusClick()
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
}

/**
 * Tab row for filtering screenshots by status
 */
@Composable
fun ScreenshotTabRow(
    currentTab: ScreenshotTab,
    onTabSelected: (ScreenshotTab) -> Unit,
    modifier: Modifier = Modifier
) {
    PrimaryTabRow(
        selectedTabIndex = currentTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        ScreenshotTab.entries.forEach { tab ->
            Tab(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
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
}

/**
 * Empty state screen for when no screenshots match the current filter
 */
@Composable
fun EmptyStateScreen(
    tab: ScreenshotTab,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.fillMaxSize(),
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

/**
 * Loading screen with circular progress indicator
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading_screenshots),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Lazy column displaying a list of screenshots with pull-to-refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotList(
    screenshots: List<MediaItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    pullRefreshState: PullToRefreshState,
    liveVideoPreviewEnabled: Boolean,
    deletingIds: Set<Long>,
    onScreenshotClick: (MediaItem) -> Unit,
    onKeepClick: (MediaItem) -> Unit,
    onUnkeepClick: (MediaItem) -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val pullOffset = pullRefreshState.distanceFraction
    val animatedPullOffset = animateFloatAsState(pullOffset * 50f).value

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .offset(y = animatedPullOffset.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = screenshots,
            key = { it.id }
        ) { screenshot ->
            AnimatedVisibility(
                visible = screenshot.id !in deletingIds,
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
                    isRefreshing = isLoading,
                    liveVideoPreviewEnabled = liveVideoPreviewEnabled,
                    onClick = { onScreenshotClick(screenshot) },
                    onKeepClick = { onKeepClick(screenshot) },
                    onUnkeepClick = { onUnkeepClick(screenshot) },
                    onDeleteClick = { onDeleteClick(screenshot) }
                )
            }
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

/**
 * Small colored status chip for screenshot status
 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/**
 * Status section showing the current state of a screenshot
 */
@Composable
private fun StatusSection(screenshot: MediaItem) {
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
            // Only update timer for items with deletion timestamps
            var currentTimeForTimer by remember(screenshot.deletionTimestamp) {
                mutableStateOf(
                    System.currentTimeMillis()
                )
            }

            LaunchedEffect(screenshot.deletionTimestamp) {
                while (true) {
                    delay(1000) // Update every second
                    currentTimeForTimer = System.currentTimeMillis()
                }
            }

            val remaining = screenshot.deletionTimestamp!! - currentTimeForTimer
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
            val statusText = stringResource(R.string.unmarked)
            StatusChip(
                text = statusText,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Action buttons for keeping/unkeeping and deleting a screenshot
 */
@Composable
private fun ActionButtons(
    screenshot: MediaItem,
    onKeepClick: () -> Unit,
    onUnkeepClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        if (screenshot.isKept) {
            OutlinedIconButton(
                onClick = onUnkeepClick,
                modifier = Modifier.size(40.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.unkeep_screenshot),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        } else {
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
            border = BorderStroke(1.dp, ErrorRed)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_screenshot),
                tint = ErrorRed
            )
        }
    }
}

/**
 * Thumbnail section displaying image or video preview
 */
@Composable
private fun ThumbnailSection(
    screenshot: MediaItem,
    liveVideoPreviewEnabled: Boolean,
    isVideo: Boolean,
    videoThumbnail: Bitmap?
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(100.dp)
    ) {
        if (isVideo) {
            DebugLogger.debug(
                "ThumbnailSection",
                "Video ${screenshot.fileName}: liveVideoPreviewEnabled=$liveVideoPreviewEnabled"
            )
        }
        if (isVideo && liveVideoPreviewEnabled) {
            val videoUri = screenshot.contentUri?.let { android.net.Uri.parse(it) }
                ?: android.net.Uri.fromFile(File(screenshot.filePath))

            VisibilityAwareVideo(
                videoId = screenshot.filePath,
                videoUri = videoUri,
                modifier = Modifier.fillMaxSize()
            ) { player, isCurrentlyPlaying ->
                DebugLogger.info(
                    "ThumbnailSection",
                    "Rendering for ${screenshot.fileName}: player=${player != null}, isPlaying=$isCurrentlyPlaying"
                )
                if (player != null) {
                    DebugLogger.debug(
                        "ThumbnailSection",
                        "Creating AndroidView for ${screenshot.fileName}"
                    )
                    AndroidView(
                        factory = { ctx ->
                            DebugLogger.debug(
                                "ThumbnailSection",
                                "Factory for ${screenshot.fileName}"
                            )
                            androidx.media3.ui.PlayerView(ctx).apply {
                                setPlayer(player)
                                useController = false
                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_ALWAYS)
                                resizeMode =
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            DebugLogger.debug(
                                "ThumbnailSection",
                                "Update AndroidView for ${screenshot.fileName}: player=${view.player != null}"
                            )
                            view.player = player
                            view.player?.playWhenReady = true
                        }
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(
                                if (videoThumbnail != null) videoThumbnail else {
                                    screenshot.contentUri?.let { android.net.Uri.parse(it) }
                                        ?: File(
                                            screenshot.filePath
                                        )
                                })
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.screenshot_thumbnail),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.ic_launcher_foreground)
                    )

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                        tint = Color.White.copy(alpha = 0.7f)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(24.dp)
                            .background(Color.Red.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(
                        if (isVideo && videoThumbnail != null) videoThumbnail else {
                            screenshot.contentUri?.let { android.net.Uri.parse(it) } ?: File(
                                screenshot.filePath
                            )
                        })
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.screenshot_thumbnail),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_launcher_foreground)
            )
            if (isVideo) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Content section showing filename, size, and status
 */
@Composable
private fun ContentSection(
    screenshot: MediaItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = screenshot.fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        val sizeText = getFileSizeText(screenshot.filePath)

        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusSection(screenshot)
    }
}

/**
 * Individual screenshot card showing thumbnail, info, and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotCard(
    screenshot: MediaItem,
    isRefreshing: Boolean = false,
    liveVideoPreviewEnabled: Boolean = false,
    onClick: () -> Unit,
    onKeepClick: () -> Unit,
    onUnkeepClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alpha = animateFloatAsState(if (isRefreshing) 0.7f else 1f).value

    val isVideo = isVideoFile(screenshot.filePath)
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(isVideo, screenshot.filePath) {
        if (isVideo) {
            videoThumbnail = loadVideoThumbnail(context, screenshot)
        }
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .alpha(alpha)
            .animateContentSize() // Add smooth animation for content changes
            .clickable(onClick = onClick, enabled = true),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThumbnailSection(screenshot, liveVideoPreviewEnabled, isVideo, videoThumbnail)

            Spacer(modifier = Modifier.width(16.dp))

            ContentSection(screenshot, modifier = Modifier.weight(1f))

            ActionButtons(screenshot, onKeepClick, onUnkeepClick, onDeleteClick)
        }
    }
}