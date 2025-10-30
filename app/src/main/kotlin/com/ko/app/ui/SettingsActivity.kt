package com.ko.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ko.app.ScreenshotApp
import com.ko.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val FIVE_MINUTES = 5L
private const val FIFTEEN_MINUTES = 15L

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var app: ScreenshotApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as ScreenshotApp

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val isManualMode = app.preferences.isManualMarkMode.first()
            binding.manualMarkSwitch.isChecked = isManualMode
            updateDeletionTimeCardVisibility(isManualMode)

            val deletionTime = app.preferences.deletionTimeMillis.first()
            binding.deletionTimeText.text = formatDeletionTime(deletionTime)

            val notificationsEnabled = app.preferences.notificationsEnabled.first()
            binding.notificationsSwitch.isChecked = notificationsEnabled

            val folder = app.preferences.screenshotFolder.first()
            binding.folderPathText.text = folder
        }
    }

    private fun setupListeners() {
        binding.manualMarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.preferences.setManualMarkMode(isChecked)
                updateDeletionTimeCardVisibility(isChecked)
            }
        }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.preferences.setNotificationsEnabled(isChecked)
            }
        }

        binding.btnChangeDeletionTime.setOnClickListener {
            showDeletionTimeDialog()
        }

        binding.btnChangeFolder.setOnClickListener {
            showFolderDialog()
        }
    }

    private fun updateDeletionTimeCardVisibility(isManualMode: Boolean) {
        binding.deletionTimeCard.visibility = if (isManualMode) View.GONE else View.VISIBLE
    }

    private fun showDeletionTimeDialog() {
        val options = arrayOf(
            "5 minutes",
            "15 minutes",
            "30 minutes",
            "1 hour",
            "2 hours",
            "6 hours",
            "12 hours",
            "1 day",
            "3 days",
            "1 week"
        )

        val values = longArrayOf(
            TimeUnit.MINUTES.toMillis(FIVE_MINUTES),
            TimeUnit.MINUTES.toMillis(FIFTEEN_MINUTES),
            TimeUnit.MINUTES.toMillis(30),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.HOURS.toMillis(2),
            TimeUnit.HOURS.toMillis(6),
            TimeUnit.HOURS.toMillis(12),
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.DAYS.toMillis(3),
            TimeUnit.DAYS.toMillis(7)
        )

        AlertDialog.Builder(this)
            .setTitle("Select Deletion Time")
            .setItems(options) { _, which ->
                lifecycleScope.launch {
                    app.preferences.setDeletionTimeMillis(values[which])
                    binding.deletionTimeText.text = options[which]
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFolderDialog() {
        AlertDialog.Builder(this)
            .setTitle("Screenshot Folder")
            .setMessage(
                "Current folder: ${binding.folderPathText.text}\n\n" +
                    "Note: Folder selection is currently set to default. " +
                    "Custom folder selection requires additional implementation."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatDeletionTime(millis: Long): String {
        return when {
            millis < TimeUnit.HOURS.toMillis(1) -> {
                "${TimeUnit.MILLISECONDS.toMinutes(millis)} minutes"
            }
            millis < TimeUnit.DAYS.toMillis(1) -> {
                "${TimeUnit.MILLISECONDS.toHours(millis)} hours"
            }
            else -> {
                "${TimeUnit.MILLISECONDS.toDays(millis)} days"
            }
        }
    }
}
