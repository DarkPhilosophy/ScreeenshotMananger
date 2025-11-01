package com.ko.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.annotation.Keep
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.ko.app.R
import com.ko.app.ScreenshotApp
import com.ko.app.databinding.ActivityMainBinding
import com.ko.app.service.ScreenshotMonitorService
import com.ko.app.ui.adapter.ScreenshotAdapter
import com.ko.app.util.DebugLogger
import com.ko.app.util.NotificationHelper
import com.ko.app.util.PermissionUtils
import com.ko.app.util.WorkManagerScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("TooManyFunctions")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var app: ScreenshotApp
    private lateinit var adapter: ScreenshotAdapter
    private var currentTab = 0
    private var screenshotsJob: Job? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkAndRequestNotificationPermission()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkAndRequestOverlayPermission()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as ScreenshotApp

        setupToolbar()
        setupRecyclerView()
        setupTabs()
        setupServiceSwitch()
        setupFab()

        observeScreenshots()
        observeServiceStatus()

        // Scan existing screenshots on app start if permissions granted
        if (PermissionUtils.hasStoragePermission(this)) {
            scanExistingScreenshots()
        }

        // Also scan in service if running, but since service scans on start, ok

        lifecycleScope.launch {
            if (app.preferences.isFirstLaunch.first()) {
                showWelcomeDialog()
                app.preferences.setFirstLaunch(false)

                if (app.preferences.serviceEnabled.first()) {
                    val serviceIntent = Intent(this@MainActivity, ScreenshotMonitorService::class.java).apply {
                        putExtra("scan_existing", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupRecyclerView() {
        adapter = ScreenshotAdapter(
            onKeepClick = { screenshot ->
                lifecycleScope.launch {
                    app.repository.markAsKept(screenshot.id)
                    val notificationHelper = NotificationHelper(this@MainActivity)
                    notificationHelper.cancelNotification(screenshot.id.toInt())
                }
            },
            onDeleteClick = { screenshot ->
                showDeleteConfirmationDialog(screenshot.id, screenshot.filePath)
            },
            onImageClick = { screenshot ->
                openScreenshot(screenshot)
            }
        )

        binding.screenshotsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Marked"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Kept"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                observeScreenshots()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // No action needed
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // No action needed
            }
        })
    }

    private fun setupServiceSwitch() {
        lifecycleScope.launch {
            val isEnabled = app.preferences.serviceEnabled.first()
            binding.serviceSwitch.isChecked = isEnabled
            updateServiceStatus(isEnabled)
        }

        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                if (isChecked) {
                    val missingPerms = getMissingPermissions()
                    if (missingPerms.isEmpty()) {
                        startMonitoringService()
                    } else {
                        binding.serviceSwitch.isChecked = false
                        showMissingPermissionsDialog(missingPerms)
                    }
                } else {
                    stopMonitoringService()
                }
            }
        }
    }

    private fun setupFab() {
        binding.fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun observeScreenshots() {
        screenshotsJob?.cancel()
        screenshotsJob = lifecycleScope.launch {
            binding.loadingProgress.visibility = View.VISIBLE
            binding.screenshotsRecyclerView.visibility = View.GONE
            try {
                when (currentTab) {
                    0 -> app.repository.getMarkedScreenshots()
                    1 -> app.repository.getKeptScreenshots()
                    else -> app.repository.getAllScreenshots()
                }.collect { screenshots ->
                    if (screenshotsJob?.isActive == false) {
                        DebugLogger.info("MainActivity", "Screenshot collection cancelled - user switched tabs")
                        return@collect
                    }

                    withContext(Dispatchers.Main) {
                        adapter.submitList(screenshots)
                        binding.emptyStateText.visibility =
                            if (screenshots.isEmpty()) View.VISIBLE else View.GONE
                        binding.loadingProgress.visibility = View.GONE
                        binding.screenshotsRecyclerView.visibility = View.VISIBLE
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                    binding.screenshotsRecyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun observeServiceStatus() {
        lifecycleScope.launch {
            app.preferences.serviceEnabled.collect { isEnabled ->
                updateServiceStatus(isEnabled)
            }
        }
    }

    private fun updateServiceStatus(isEnabled: Boolean) {
        binding.serviceStatusText.text = if (isEnabled) {
            "Service is running"
        } else {
            "Service is stopped"
        }
    }

    private fun scanExistingScreenshots() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DebugLogger.info("MainActivity", "Scanning existing screenshots on app start")

                val configuredFolder = app.preferences.screenshotFolder.first()
                val screenshotFolder = if (configuredFolder.isNotEmpty() && configuredFolder != "Pictures/Screenshots") {
                    // Decode URI to path
                    java.net.URLDecoder.decode(configuredFolder, "UTF-8").let { decoded ->
                        when {
                            decoded.contains("primary:") -> Environment.getExternalStorageDirectory().absolutePath + "/" + decoded.substringAfter("primary:")
                            decoded.contains("tree/") -> {
                                val parts = decoded.substringAfter("tree/").split(":")
                                if (parts.size >= 2) {
                                    val path = parts[1]
                                    Environment.getExternalStorageDirectory().absolutePath + "/" + path
                                } else decoded
                            }
                            else -> decoded
                        }
                    }
                } else {
                    // Default
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
                }

                DebugLogger.info("MainActivity", "Scanning folder: $screenshotFolder")
                val folder = File(screenshotFolder)
                if (!folder.exists() || !folder.isDirectory) {
                    DebugLogger.warning(
                        "MainActivity",
                        "Screenshot folder doesn't exist: $screenshotFolder, exists=${folder.exists()}, isDir=${folder.isDirectory}"
                    )
                    return@launch
                }

                val imageFiles = folder.listFiles { file ->
                    file.isFile && (file.extension.lowercase() in listOf("png", "jpg", "jpeg"))
                }

                val count = imageFiles?.size ?: 0
                DebugLogger.info("MainActivity", "Found $count existing screenshot files")

                var imported = 0
                imageFiles?.forEach { file ->
                    val existing = app.repository.getByFilePath(file.absolutePath)
                    if (existing == null && file.exists() && file.length() > 0) {
                        val screenshot = com.ko.app.data.entity.Screenshot(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            fileSize = file.length(),
                            createdAt = file.lastModified(),
                            deletionTimestamp = null,
                            isMarkedForDeletion = false,
                            isKept = false
                        )
                        app.repository.insert(screenshot)
                        imported++
                    }
                }
                DebugLogger.info("MainActivity", "Imported $imported new screenshots from existing files")
            } catch (e: Exception) {
                DebugLogger.error("MainActivity", "Error scanning existing screenshots", e)
            }
        }
    }

    private fun getMissingPermissions(): List<String> {
        return PermissionUtils.getMissingPermissions(this)
    }

    private fun showDetailedPermissionsStatus() {
        val storageGranted = PermissionUtils.hasStoragePermission(this)
        val notificationGranted = PermissionUtils.hasNotificationPermission(this)
        val overlayGranted = PermissionUtils.hasOverlayPermission(this)

        val allGranted = storageGranted && notificationGranted && overlayGranted

        val greenCheck = "✓"
        val redX = "✗"

        val message = android.text.SpannableStringBuilder().apply {
            append("Read Screenshot     ")
            val start1 = length
            append(if (storageGranted) greenCheck else redX)
            if (storageGranted) {
                setSpan(android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()), start1, length, 0)
            } else {
                setSpan(android.text.style.ForegroundColorSpan(0xFFF44336.toInt()), start1, length, 0)
            }
            append("\n")

            append("Notification Access ")
            val start2 = length
            append(if (notificationGranted) greenCheck else redX)
            if (notificationGranted) {
                setSpan(android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()), start2, length, 0)
            } else {
                setSpan(android.text.style.ForegroundColorSpan(0xFFF44336.toInt()), start2, length, 0)
            }
            append("\n")

            append("Overlay Permission  ")
            val start3 = length
            append(if (overlayGranted) greenCheck else redX)
            if (overlayGranted) {
                setSpan(android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()), start3, length, 0)
            } else {
                setSpan(android.text.style.ForegroundColorSpan(0xFFF44336.toInt()), start3, length, 0)
            }
            append("\n\n")

            if (allGranted) {
                val statusStart = length
                append("😁 Ready")
                setSpan(android.text.style.ForegroundColorSpan(0xFF4CAF50.toInt()), statusStart, length, 0)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), statusStart, length, 0)
            } else {
                val statusStart = length
                append("⚠️ Missing permissions")
                setSpan(android.text.style.ForegroundColorSpan(0xFFF44336.toInt()), statusStart, length, 0)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Permission Status")
            .setMessage(message)
            .setPositiveButton(if (allGranted) "OK" else "Go to Settings") { _, _ ->
                if (!allGranted) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            }
            .setNegativeButton(if (allGranted) null else "Cancel", null)
            .show()
    }

    private fun showMissingPermissionsDialog(missingPerms: List<String>) {
        val message = "The following permissions are required:\n\n" +
            missingPerms.joinToString("\n") { "• $it" }

        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        storagePermissionLauncher.launch(permissions)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestOverlayPermission()
            }
        } else {
            checkAndRequestOverlayPermission()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        } else {
            checkAndRequestBatteryOptimization()
        }
    }

    private fun checkAndRequestBatteryOptimization() {
        startMonitoringService()
    }

    private fun startMonitoringService() {
        lifecycleScope.launch {
            app.preferences.setServiceEnabled(true)

            val serviceIntent = Intent(this@MainActivity, ScreenshotMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            WorkManagerScheduler.scheduleDeletionWork(this@MainActivity)

            binding.serviceSwitch.isChecked = true
        }
    }

    private fun stopMonitoringService() {
        lifecycleScope.launch {
            app.preferences.setServiceEnabled(false)

            val serviceIntent = Intent(this@MainActivity, ScreenshotMonitorService::class.java)
            stopService(serviceIntent)

            WorkManagerScheduler.cancelDeletionWork(this@MainActivity)

            binding.serviceSwitch.isChecked = false
        }
    }

    private fun showDeleteConfirmationDialog(screenshotId: Long, filePath: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Screenshot")
            .setMessage("Are you sure you want to delete this screenshot?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val file = File(filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    app.repository.deleteById(screenshotId)

                    val notificationHelper = NotificationHelper(this@MainActivity)
                    notificationHelper.cancelNotification(screenshotId.toInt())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Screenshot Manager")
            .setMessage(
                "This app helps you automatically manage screenshots.\n\n" +
                    "Features:\n" +
                    "• Auto-delete screenshots after a set time\n" +
                    "• Manual mark mode for custom deletion times\n" +
                    "• Keep important screenshots forever\n\n" +
                    "To get started, enable the monitoring service and grant the required permissions."
            )
            .setPositiveButton("Get Started", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app requires storage and notification permissions to function properly.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("This app needs overlay permission to show quick action popups for screenshots.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_permissions -> {
                showDetailedPermissionsStatus()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openScreenshot(screenshot: com.ko.app.data.entity.Screenshot) {
        try {
            val file = File(screenshot.filePath)
            if (!file.exists()) {
                AlertDialog.Builder(this)
                    .setTitle("File Not Found")
                    .setMessage("The screenshot file no longer exists.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to open screenshot: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
