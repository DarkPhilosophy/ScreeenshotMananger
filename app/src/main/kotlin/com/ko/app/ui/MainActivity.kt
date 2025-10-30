package com.ko.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.ko.app.R
import com.ko.app.ScreenshotApp
import com.ko.app.databinding.ActivityMainBinding
import com.ko.app.service.ScreenshotMonitorService
import com.ko.app.ui.adapter.ScreenshotAdapter
import com.ko.app.util.NotificationHelper
import com.ko.app.util.WorkManagerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@Suppress("TooManyFunctions")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var app: ScreenshotApp
    private lateinit var adapter: ScreenshotAdapter
    private var currentTab = 0

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

        lifecycleScope.launch {
            if (app.preferences.isFirstLaunch.first()) {
                showWelcomeDialog()
                app.preferences.setFirstLaunch(false)
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
                    if (checkPermissions()) {
                        startMonitoringService()
                    } else {
                        binding.serviceSwitch.isChecked = false
                        requestPermissions()
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
        lifecycleScope.launch {
            when (currentTab) {
                0 -> app.repository.getMarkedScreenshots()
                1 -> app.repository.getKeptScreenshots()
                else -> app.repository.getAllScreenshots()
            }.collect { screenshots ->
                adapter.submitList(screenshots)
                binding.emptyStateText.visibility =
                    if (screenshots.isEmpty()) View.VISIBLE else View.GONE
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

    private fun checkPermissions(): Boolean {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        return storagePermission && notificationPermission && overlayPermission
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog()
            } else {
                checkAndRequestBatteryOptimization()
            }
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
                    Uri.parse("package:$packageName")
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
                requestPermissions()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
