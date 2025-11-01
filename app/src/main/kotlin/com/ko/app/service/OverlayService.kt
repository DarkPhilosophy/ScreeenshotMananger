package com.ko.app.service

import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.google.android.material.button.MaterialButton
import com.ko.app.R
import com.ko.app.ScreenshotApp
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import com.ko.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val FIFTEEN_MINUTES = 15L
private const val THREE_DAYS = 3L
private const val ONE_WEEK = 7L
private const val ANIMATION_TRANSLATION_Y = 100f
private const val ANIMATION_DURATION_MS = 300L
private const val DISMISS_ANIMATION_DURATION_MS = 200L
private const val DISMISS_TRANSLATION_Y = -100f

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var notificationHelper: NotificationHelper
    private var screenshotId: Long = -1L
    private var filePath: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationHelper = NotificationHelper(this)
        screenshotId = intent?.getLongExtra("screenshot_id", -1L) ?: -1L
        filePath = intent?.getStringExtra("file_path") ?: ""

        DebugLogger.info("OverlayService", "onStartCommand called with screenshot ID: $screenshotId, path: $filePath")

        if (screenshotId != -1L) {
            try {
                if (PermissionUtils.hasOverlayPermission(this)) {
                    showOverlay()
                } else {
                    DebugLogger.error(
                        "OverlayService",
                        "Overlay permission not granted - manual mode requires overlay permission"
                    )
                    notificationHelper.showErrorNotification("Manual Mode Error", "Overlay permission required. Grant in app settings.")
                    stopSelf()
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                DebugLogger.error("OverlayService", "CRASH in manual mode: ${e.javaClass.simpleName} - ${e.message}", e)
                notificationHelper.showErrorNotification("Manual Mode Crashed", "Error: ${e.javaClass.simpleName} - ${e.message}")
                stopSelf()
            }
        } else {
            DebugLogger.error("OverlayService", "Invalid screenshot ID")
            stopSelf()
        }

        return START_NOT_STICKY
    }

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
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_screenshot_options, null, false)
            setupButtons()
            val wm = windowManager
            wm.addView(overlayView, params)
            DebugLogger.info("OverlayService", "Overlay view added successfully")
            animateOverlayIn()
        } catch (e: WindowManager.BadTokenException) {
            DebugLogger.error("OverlayService", "BadTokenException - window token invalid", e)
            overlayView = null
            stopSelf()
        } catch (e: SecurityException) {
            DebugLogger.error("OverlayService", "SecurityException - permission denied", e)
            overlayView = null
            stopSelf()
        } catch (e: IllegalStateException) {
            DebugLogger.error("OverlayService", "IllegalStateException - invalid state", e)
            overlayView = null
            stopSelf()
        } catch (e: IllegalArgumentException) {
            DebugLogger.error("OverlayService", "IllegalArgumentException - invalid layout params", e)
            overlayView = null
            stopSelf()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            DebugLogger.error("OverlayService", "Crash prevented: ${e.javaClass.simpleName} - ${e.message}", e)
            overlayView = null
            stopSelf()
        }
    }

    private fun setupButtons() {
        overlayView?.apply {
            findViewById<MaterialButton>(R.id.btn15Minutes).setOnClickListener {
                handleDeletionTime(TimeUnit.MINUTES.toMillis(FIFTEEN_MINUTES))
            }

            findViewById<MaterialButton>(R.id.btn2Hours).setOnClickListener {
                handleDeletionTime(TimeUnit.HOURS.toMillis(2))
            }

            findViewById<MaterialButton>(R.id.btn3Days).setOnClickListener {
                handleDeletionTime(TimeUnit.DAYS.toMillis(THREE_DAYS))
            }

            findViewById<MaterialButton>(R.id.btn1Week).setOnClickListener {
                handleDeletionTime(TimeUnit.DAYS.toMillis(ONE_WEEK))
            }

            findViewById<MaterialButton>(R.id.btnKeep).setOnClickListener {
                handleKeep()
            }
        }
    }

    private fun handleDeletionTime(timeMillis: Long) {
        serviceScope.launch(Dispatchers.IO) {
            val app = application as ScreenshotApp
            val deletionTimestamp = System.currentTimeMillis() + timeMillis

            app.repository.markForDeletion(screenshotId, deletionTimestamp)

            val screenshot = app.repository.getById(screenshotId)
            screenshot?.let {
                val notificationHelper = NotificationHelper(this@OverlayService)
                notificationHelper.showScreenshotNotification(
                    screenshotId,
                    it.fileName,
                    deletionTimestamp
                )
            }

            launch(Dispatchers.Main) {
                dismissOverlay()
            }
        }
    }

    private fun handleKeep() {
        serviceScope.launch(Dispatchers.IO) {
            val app = application as ScreenshotApp
            app.repository.markAsKept(screenshotId)

            launch(Dispatchers.Main) {
                dismissOverlay()
            }
        }
    }

    private fun animateOverlayIn() {
        overlayView?.let { view ->
            view.alpha = 0f
            view.translationY = ANIMATION_TRANSLATION_Y

            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = ANIMATION_DURATION_MS
                start()
            }

            ObjectAnimator.ofFloat(view, "translationY", ANIMATION_TRANSLATION_Y, 0f).apply {
                duration = ANIMATION_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun dismissOverlay() {
        overlayView?.let { view ->
            ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = DISMISS_ANIMATION_DURATION_MS
                start()
            }

            ObjectAnimator.ofFloat(view, "translationY", 0f, DISMISS_TRANSLATION_Y).apply {
                duration = DISMISS_ANIMATION_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
            }

            view.postDelayed({
                try {
                    if (::windowManager.isInitialized) {
                        windowManager.removeView(view)
                    }
                } catch (@Suppress("TooGenericExceptionCaught", "PrintStackTrace") e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
            }, DISMISS_ANIMATION_DURATION_MS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
