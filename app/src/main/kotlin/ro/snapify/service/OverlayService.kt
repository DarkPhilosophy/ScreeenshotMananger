package ro.snapify.service

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ro.snapify.ScreenshotApp
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.data.repository.MediaRepository
import ro.snapify.ui.RefreshReason
import ro.snapify.ui.theme.AppTheme
import ro.snapify.ui.theme.ThemeMode
import ro.snapify.util.DebugLogger
import ro.snapify.util.NotificationHelper
import ro.snapify.util.PermissionUtils
import kotlin.math.abs
import kotlin.math.min

private const val FIFTEEN_MINUTES = 15L
private const val THREE_DAYS = 3L
private const val ONE_WEEK = 7L


private fun formatTime(minutes: Int): String {
    val parts = mutableListOf<String>()
    var remaining = minutes

    val years = remaining / 525600
    if (years > 0) {
        parts.add("$years year${if (years > 1) "s" else ""}")
        remaining %= 525600
    }

    val weeks = remaining / 10080
    if (weeks > 0) {
        parts.add("$weeks week${if (weeks > 1) "s" else ""}")
        remaining %= 10080
    }

    val days = remaining / 1440
    if (days > 0) {
        parts.add("$days day${if (days > 1) "s" else ""}")
        remaining %= 1440
    }

    val hours = remaining / 60
    if (hours > 0) {
        parts.add("$hours hour${if (hours > 1) "s" else ""}")
        remaining %= 60
    }

    if (remaining > 0 || parts.isEmpty()) {
        parts.add("$remaining minute${if (remaining != 1) "s" else ""}")
    }

    return parts.joinToString(" ")
}

/**
 * Composable function that displays the overlay UI for screenshot management.
 * Provides buttons for quick deletion times, custom time picker, and keep option.
 */
@Composable
private fun OverlayContent(
    on15Minutes: () -> Unit,
    on2Hours: () -> Unit,
    on3Days: () -> Unit,
    on1Week: () -> Unit,
    onKeep: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    onCustomTime: (Int) -> Unit
) {
    val alphaAnimatable = remember { Animatable(0f) }
    val translationYAnimatable = remember { Animatable(100f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        alphaAnimatable.animateTo(1f, animationSpec = tween(300))
        translationYAnimatable.animateTo(0f, animationSpec = tween(300))
    }

    val isOLED = MaterialTheme.colorScheme.surface == Color.Black
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp)
            .alpha(alphaAnimatable.value)
            .graphicsLayer(translationY = translationYAnimatable.value)
            .then(
                if (isOLED) Modifier.border(
                    1.dp,
                    Color.White,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screenshot Detect",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))

            Text(
                text = "Select a time when the screenshot should be deleted otherwise you can keep",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))

            // Row 1: 1 Week | 3 Days
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = on1Week,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = "1 Week")
                }
                Button(
                    onClick = on3Days,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = "3 Days")
                }
            }

            // Row 2: 2 Hours | 15 Min
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = on2Hours,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = "2 Hours")
                }
                Button(
                    onClick = on15Minutes,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(text = "15 Min")
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            // Row 3: Custom Time Picker
            CustomTimePicker(
                onTimeSelected = onCustomTime,
                modifier = Modifier.fillMaxWidth()
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))

            // Keep Button
            Button(
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "Keep")
            }
        }
    }
}

@Composable
private fun CustomTimePicker(
    onTimeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var minutes by remember { mutableIntStateOf(30) }
    var scrollTrigger by remember { mutableIntStateOf(0) }
    var initialY by remember { mutableFloatStateOf(0f) }
    var adjustmentMode by remember { mutableIntStateOf(0) } // 0 none, 1 increase, -1 decrease
    var currentDistance by remember { mutableFloatStateOf(0f) }
    var modeStartTime by remember { mutableStateOf(0L) }
    val textScale = remember { Animatable(1f) }
    val gearRotation = remember { Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(scrollTrigger) {
// Animate text scale in/out
        textScale.animateTo(1.2f, tween(150))
        textScale.animateTo(1f, tween(150))
// Animate gear spin clockwise
        gearRotation.animateTo(gearRotation.value + 360f, tween(400))
    }

    androidx.compose.runtime.LaunchedEffect(adjustmentMode) {
        while (adjustmentMode != 0) {
            val elapsed = System.currentTimeMillis() - modeStartTime
            val baseSpeed = min(1f + (elapsed / 1000f) * 1f + (currentDistance / 100f) * 1f, 50f)
            val dayMultiplier = if (minutes >= 1440) 5f else 1f
            val speedFactor = min(baseSpeed * dayMultiplier, 200f)
            val increment = min(speedFactor.toInt() / 5, 60).coerceAtLeast(1)
            if (adjustmentMode == 1) {
                minutes = (minutes + increment).coerceAtMost(Int.MAX_VALUE)
                scrollTrigger++
            } else if (adjustmentMode == -1) {
                minutes = (minutes - increment).coerceAtLeast(1)
                scrollTrigger++
            }
            val delay = (200L / speedFactor).toLong().coerceAtLeast(5L)
            kotlinx.coroutines.delay(delay)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            initialY = offset.y
                            adjustmentMode = 0
                            currentDistance = 0f
                        },
                        onDragEnd = { adjustmentMode = 0 },
                        onDragCancel = { adjustmentMode = 0 }
                    ) { change, _ ->
                        val currentY = change.position.y
                        val newDistance = abs(currentY - initialY)
                        currentDistance = newDistance
                        val newMode =
                            if (currentY < initialY) 1 else if (currentY > initialY) -1 else 0
                        if (adjustmentMode != newMode) {
                            modeStartTime = System.currentTimeMillis()
                            adjustmentMode = newMode
                        }
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Wheel",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(rotationZ = gearRotation.value),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = formatTime(minutes),
                    modifier = Modifier
                        .clickable { onTimeSelected(minutes) }
                        .graphicsLayer(scaleX = textScale.value, scaleY = textScale.value),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: MediaRepository
    private lateinit var refreshFlow: kotlinx.coroutines.flow.MutableSharedFlow<RefreshReason>
    private lateinit var preferences: AppPreferences
    private var screenshotId: Long = -1L
    private var filePath: String = ""

    private fun handleOverlayException(e: Exception, tag: String = "OverlayService") {
        DebugLogger.error(tag, "Exception: ${e.javaClass.simpleName} - ${e.message}", e)
        overlayView = null
        stopSelf()
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(android.os.Bundle())
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        screenshotId = intent?.getLongExtra("screenshot_id", -1L) ?: -1L
        filePath = intent?.getStringExtra("file_path") ?: ""

        DebugLogger.info(
            "OverlayService",
            "onStartCommand called with screenshot ID: $screenshotId, path: $filePath"
        )

        // Initialize dependencies from application
        val app = application as ScreenshotApp
        repository = app.repository
        preferences = app.preferences
        refreshFlow = app.refreshFlow

        if (screenshotId > 0L) {
            try {
                if (PermissionUtils.hasOverlayPermission(this)) {
                    showOverlay()
                } else {
                    DebugLogger.error(
                        "OverlayService",
                        "Overlay permission not granted - manual mode requires overlay permission"
                    )
                    NotificationHelper.showErrorNotification(
                        this,
                        "Manual Mode Error",
                        "Overlay permission required. Grant in app settings."
                    )
                    showFallbackNotification()
                    stopSelf()
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                DebugLogger.error(
                    "OverlayService",
                    "CRASH in manual mode: ${e.javaClass.simpleName} - ${e.message}",
                    e
                )
                NotificationHelper.showErrorNotification(
                    this,
                    "Manual Mode Crashed",
                    "Error: ${e.javaClass.simpleName} - ${e.message}"
                )
                showFallbackNotification()
                stopSelf()
            }
        } else {
            DebugLogger.error("OverlayService", "Invalid screenshot ID: $screenshotId")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        try {
            DebugLogger.info("OverlayService", "Attempting to show overlay")

            if (overlayView != null) {
                DebugLogger.warning("OverlayService", "Overlay already shown, skipping")
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            // Create ComposeView for the overlay
            overlayView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent {
                    val themeModeString = runBlocking { preferences.themeMode.first() }
                    val themeMode = when (themeModeString) {
                        "light" -> ThemeMode.LIGHT
                        "dark" -> ThemeMode.DARK
                        "dynamic" -> ThemeMode.DYNAMIC
                        "oled" -> ThemeMode.OLED
                        else -> ThemeMode.SYSTEM
                    }

                    AppTheme(themeMode = themeMode, skipWindowSetup = true) {
                        val dismissWithNotification =
                            { dismissOverlay(showFallbackNotification = true) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { dismissWithNotification() },
                            contentAlignment = Alignment.Center
                        ) {
                            OverlayContent(
                                on15Minutes = {
                                    handleDeletionTime(
                                        java.util.concurrent.TimeUnit.MINUTES.toMillis(
                                            FIFTEEN_MINUTES
                                        )
                                    )
                                },
                                on2Hours = {
                                    handleDeletionTime(
                                        java.util.concurrent.TimeUnit.HOURS.toMillis(
                                            2
                                        )
                                    )
                                },
                                on3Days = {
                                    handleDeletionTime(
                                        java.util.concurrent.TimeUnit.DAYS.toMillis(
                                            THREE_DAYS
                                        )
                                    )
                                },
                                on1Week = {
                                    handleDeletionTime(
                                        java.util.concurrent.TimeUnit.DAYS.toMillis(
                                            ONE_WEEK
                                        )
                                    )
                                },
                                onKeep = { handleKeep() },
                                onClose = { handleClose() },
                                onDismiss = dismissWithNotification,
                                onCustomTime = { minutes ->
                                    handleDeletionTime(
                                        java.util.concurrent.TimeUnit.MINUTES.toMillis(
                                            minutes.toLong()
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            val wm = windowManager
            wm.addView(overlayView, params)

            // CRITICAL: Set lifecycle to RESUMED so Compose can render
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED

            DebugLogger.info("OverlayService", "Overlay view added successfully")
            animateOverlayIn()
        } catch (e: WindowManager.BadTokenException) {
            handleOverlayException(e)
        } catch (e: SecurityException) {
            handleOverlayException(e)
        } catch (e: IllegalStateException) {
            handleOverlayException(e)
        } catch (e: IllegalArgumentException) {
            handleOverlayException(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            DebugLogger.error(
                "OverlayService",
                "Crash prevented: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
            overlayView = null
            stopSelf()
        }
    }


    private fun handleDeletionTime(timeMillis: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val deletionTimestamp = System.currentTimeMillis() + timeMillis

            // Update DB synchronously
            runBlocking {
                repository.markForDeletion(screenshotId, deletionTimestamp)
            }

            // The service's deletion check timer will handle the deletion

            // Show notification to confirm the scheduled deletion
            val screenshot = repository.getById(screenshotId)
            screenshot?.let {
                NotificationHelper.showScreenshotNotification(
                    this@OverlayService,
                    screenshotId,
                    it.fileName,
                    it.filePath,
                    deletionTimestamp,
                    timeMillis,
                    isManualMode = false, // Show countdown like automatic mode
                    preferences = preferences
                )
                DebugLogger.info(
                    "OverlayService",
                    "Notification shown after time selection in manual mode"
                )
            }

            withContext(Dispatchers.Main) {
                // Notify UI to refresh
                DebugLogger.info(
                    "OverlayService",
                    "Emitting refreshFlow after marking for deletion"
                )
                refreshFlow.tryEmit(RefreshReason.Other)
                dismissOverlay(showFallbackNotification = false)
            }
        }
    }

    private fun handleKeep() {
        serviceScope.launch(Dispatchers.IO) {
            repository.markAsKept(screenshotId)

            withContext(Dispatchers.Main) {
                // Notify UI to refresh
                DebugLogger.info("OverlayService", "Emitting refreshFlow after keeping screenshot")
                refreshFlow.tryEmit(RefreshReason.Other)
                dismissOverlay(showFallbackNotification = false)
            }
        }
    }

    private fun handleClose() {
        serviceScope.launch(Dispatchers.IO) {
            repository.markAsKept(screenshotId)

            withContext(Dispatchers.Main) {
                dismissOverlay(showFallbackNotification = false)
            }
        }
    }

    private fun animateOverlayIn() {
        // Animation is now handled by Compose
        DebugLogger.info("OverlayService", "Overlay animation in (handled by Compose)")
    }

    private fun dismissOverlay(showFallbackNotification: Boolean = true) {
        overlayView?.let { view ->
            try {
                if (::windowManager.isInitialized) {
                    windowManager.removeView(view)
                }
                overlayView = null
            } catch (
                @Suppress(
                    "TooGenericExceptionCaught",
                    "PrintStackTrace"
                ) e: Exception
            ) {
                e.printStackTrace()
                overlayView = null
            }

            // If overlay was dismissed without user interaction, show notification as fallback
            if (showFallbackNotification) {
                showFallbackNotification()
            } else {
                stopSelf()
            }
        } ?: run {
            stopSelf()
        }
    }

    private fun showFallbackNotification() {
        serviceScope.launch(Dispatchers.IO) {
            val screenshot = repository.getById(screenshotId)
            screenshot?.let {
                val deletionTime = preferences.deletionTimeMillis.first()
                val deletionTimestamp = System.currentTimeMillis() + deletionTime
                NotificationHelper.showScreenshotNotification(
                    this@OverlayService,
                    screenshotId,
                    it.fileName,
                    it.filePath,
                    deletionTimestamp,
                    deletionTime,
                    isManualMode = true,
                    preferences = preferences
                )
                DebugLogger.info("OverlayService", "Fallback notification shown for manual mode")
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
        // Cancel any running coroutines to avoid leaks
        try {
            serviceScope.cancel()
        } catch (_: Exception) {
            // ignore
        }
        overlayView?.let {
            try {
                if (::windowManager.isInitialized) {
                    windowManager.removeView(it)
                }
            } catch (@Suppress("TooGenericExceptionCaught", "PrintStackTrace") e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
