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
import com.ko.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var screenshotId: Long = -1L
    private var filePath: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        screenshotId = intent?.getLongExtra("screenshot_id", -1L) ?: -1L
        filePath = intent?.getStringExtra("file_path") ?: ""

        if (screenshotId != -1L) {
            showOverlay()
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showOverlay() {
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

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_screenshot_options, null)

        setupButtons()

        windowManager?.addView(overlayView, params)

        animateOverlayIn()
    }

    private fun setupButtons() {
        overlayView?.apply {
            findViewById<MaterialButton>(R.id.btn15Minutes).setOnClickListener {
                handleDeletionTime(TimeUnit.MINUTES.toMillis(15))
            }

            findViewById<MaterialButton>(R.id.btn2Hours).setOnClickListener {
                handleDeletionTime(TimeUnit.HOURS.toMillis(2))
            }

            findViewById<MaterialButton>(R.id.btn3Days).setOnClickListener {
                handleDeletionTime(TimeUnit.DAYS.toMillis(3))
            }

            findViewById<MaterialButton>(R.id.btn1Week).setOnClickListener {
                handleDeletionTime(TimeUnit.DAYS.toMillis(7))
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
            view.translationY = 100f

            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }

            ObjectAnimator.ofFloat(view, "translationY", 100f, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun dismissOverlay() {
        overlayView?.let { view ->
            ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = 200
                start()
            }

            ObjectAnimator.ofFloat(view, "translationY", 0f, -100f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                start()
            }

            view.postDelayed({
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
            }, 200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

