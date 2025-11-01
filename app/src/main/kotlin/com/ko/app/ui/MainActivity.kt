package com.ko.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var currentOffset = 0
    private val pageSize = 20
    private var isLoading = false
    private var hasMore = true
    private val allScreenshots = mutableListOf<com.ko.app.data.entity.Screenshot>()
    private var isPermissionDialogOpen = false
    private var updatePermissionSwitches: (() -> Unit)? = null
    private var loadJob: Job? = null

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

        loadPagedScreenshots()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isPermissionDialogOpen) {
            updatePermissionSwitches?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionDialogOpen) {
            updatePermissionSwitches?.invoke()
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
                    refreshCurrentTab()
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

        binding.screenshotsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && hasMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                    loadPagedScreenshots()
                }
            }
        })
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_marked)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_kept)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.tab_all)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                loadJob?.cancel()
                currentTab = tab?.position ?: 0
                currentOffset = 0
                hasMore = true
                allScreenshots.clear()
                adapter.submitList(emptyList())
                loadPagedScreenshots()
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
                    showDetailedPermissionsStatus()
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

    private fun loadPagedScreenshots() {
        if (isLoading || !hasMore) return
        isLoading = true
        binding.loadingProgress.visibility = View.VISIBLE
        val targetTab = currentTab
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val newItems = when (targetTab) {
                    0 -> app.repository.getPagedMarkedScreenshots(currentOffset, pageSize)
                    1 -> app.repository.getPagedKeptScreenshots(currentOffset, pageSize)
                    else -> app.repository.getPagedScreenshots(currentOffset, pageSize)
                }
                withContext(Dispatchers.Main) {
                    if (targetTab != currentTab) return@withContext
                    if (newItems.size < pageSize) {
                        hasMore = false
                    }
                    allScreenshots.addAll(newItems)
                    adapter.submitList(allScreenshots.toList())
                    currentOffset += newItems.size
                    binding.loadingProgress.visibility = View.GONE
                    binding.emptyStateText.visibility = if (allScreenshots.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (@Suppress("SwallowedException") _: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                }
            } finally {
                isLoading = false
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
            getString(R.string.service_running)
        } else {
            getString(R.string.service_stopped)
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
        val batteryAllowed = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)

    val dialogView = layoutInflater.inflate(R.layout.permission_status_dialog, null)
    val storageSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.storageSwitch)
    val notificationSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.notificationSwitch)
    val overlaySwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.overlaySwitch)
    val batterySwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.batterySwitch)
    val statusText = dialogView.findViewById<android.widget.TextView>(R.id.statusText)
    val btnGoToSettings = dialogView.findViewById<android.widget.Button>(R.id.btnGoToSettings)
    val btnOK = dialogView.findViewById<android.widget.Button>(R.id.btnOK)

    fun updateSwitches() {
        storageSwitch.isChecked = PermissionUtils.hasStoragePermission(this)
        notificationSwitch.isChecked = PermissionUtils.hasNotificationPermission(this)
        overlaySwitch.isChecked = PermissionUtils.hasOverlayPermission(this)
        batterySwitch.isChecked = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName)

        // Prevent disabling granted permissions
        storageSwitch.isEnabled = !storageSwitch.isChecked
        notificationSwitch.isEnabled = !notificationSwitch.isChecked
        overlaySwitch.isEnabled = !overlaySwitch.isChecked
        batterySwitch.isEnabled = !batterySwitch.isChecked

        // Set green color for granted permissions
        val green = "#4CAF50".toColorInt()
        val gray = "#BDBDBD".toColorInt()
        storageSwitch.thumbTintList = ColorStateList.valueOf(if (storageSwitch.isChecked) green else gray)
        storageSwitch.trackTintList = ColorStateList.valueOf(if (storageSwitch.isChecked) green else gray)
        notificationSwitch.thumbTintList = ColorStateList.valueOf(if (notificationSwitch.isChecked) green else gray)
        notificationSwitch.trackTintList = ColorStateList.valueOf(if (notificationSwitch.isChecked) green else gray)
        overlaySwitch.thumbTintList = ColorStateList.valueOf(if (overlaySwitch.isChecked) green else gray)
        overlaySwitch.trackTintList = ColorStateList.valueOf(if (overlaySwitch.isChecked) green else gray)
        batterySwitch.thumbTintList = ColorStateList.valueOf(if (batterySwitch.isChecked) green else gray)
        batterySwitch.trackTintList = ColorStateList.valueOf(if (batterySwitch.isChecked) green else gray)

        val allGranted = storageSwitch.isChecked && notificationSwitch.isChecked && overlaySwitch.isChecked && batterySwitch.isChecked
        statusText.text = if (allGranted) getString(R.string.status_ready) else getString(R.string.status_missing)
        statusText.setTextColor(if (allGranted) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
    }

    updateSwitches()

    // Add checked change listeners for switches
    storageSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !storageGranted) {
            @Suppress("DEPRECATION")
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissions(permissions, 1001)
        }
    }
    notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked && !notificationGranted) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    overlaySwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !overlayGranted) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = "package:$packageName".toUri()
            })
        }
    }
    batterySwitch.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked && !batteryAllowed) {
            @Suppress("BatteryLife")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            })
        }
    }

    val dialog = AlertDialog.Builder(this)
    .setView(dialogView)
    .create()

    isPermissionDialogOpen = true
    updatePermissionSwitches = { updateSwitches() }

    btnGoToSettings.setOnClickListener {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", packageName, null)
    })
    dialog.dismiss()
    }
    btnOK.setOnClickListener {
    dialog.dismiss()
    }

    dialog.setOnDismissListener {
        isPermissionDialogOpen = false
        updatePermissionSwitches = null
    }

    val initialAllGranted = storageSwitch.isChecked && notificationSwitch.isChecked && overlaySwitch.isChecked && batterySwitch.isChecked
    if (initialAllGranted) {
        btnGoToSettings.visibility = View.GONE
    }

    dialog.show()
    dialog.window?.setDimAmount(0.8f)
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
            .setTitle(getString(R.string.delete_screenshot))
            .setMessage(getString(R.string.delete_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    val file = File(filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                    app.repository.deleteById(screenshotId)

                    val notificationHelper = NotificationHelper(this@MainActivity)
                    notificationHelper.cancelNotification(screenshotId.toInt())
                    refreshCurrentTab()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun refreshCurrentTab() {
        loadJob?.cancel()
        currentOffset = 0
        hasMore = true
        allScreenshots.clear()
        adapter.submitList(emptyList())
        loadPagedScreenshots()
    }

    private fun showWelcomeDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.welcome_title))
            .setMessage(getString(R.string.welcome_message))
            .setPositiveButton(getString(R.string.get_started), null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permissions_required))
            .setMessage(getString(R.string.permissions_message))
            .setPositiveButton(getString(R.string.grant_permissions)) { _, _ ->
                requestPermissions()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.overlay_required))
            .setMessage(getString(R.string.overlay_message))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.skip), null)
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
                    .setTitle(getString(R.string.file_not_found))
                    .setMessage(getString(R.string.file_not_exists))
                    .setPositiveButton(getString(R.string.ok), null)
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

            startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.error))
                .setMessage(getString(R.string.open_failed, e.message))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
    }
}
