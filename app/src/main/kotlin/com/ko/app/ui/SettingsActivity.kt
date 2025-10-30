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
private const val THIRTY_MINUTES = 30L
private const val ONE_HOUR = 1L
private const val TWO_HOURS = 2L
private const val SIX_HOURS = 6L
private const val TWELVE_HOURS = 12L
private const val ONE_DAY = 1L
private const val THREE_DAYS = 3L
private const val SEVEN_DAYS = 7L
private const val DEFAULT_TIME_VALUE = 15
private const val PADDING_HORIZONTAL = 50
private const val PADDING_TOP = 40
private const val PADDING_BOTTOM = 10
private const val PADDING_SMALL = 8
private const val PADDING_MEDIUM = 20
private const val UNIT_MINUTES = 0
private const val UNIT_HOURS = 1
private const val UNIT_DAYS = 2

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
            updateModeUI(isManualMode)

            val deletionTime = app.preferences.deletionTimeMillis.first()
            binding.deletionTimeText.text = formatDeletionTime(deletionTime)

            val notificationsEnabled = app.preferences.notificationsEnabled.first()
            binding.notificationsSwitch.isChecked = notificationsEnabled

            val folder = app.preferences.screenshotFolder.first()
            binding.folderPathText.text = folder
        }
    }

    private fun setupListeners() {
        binding.btnToggleMode.setOnClickListener {
            lifecycleScope.launch {
                val currentMode = app.preferences.isManualMarkMode.first()
                val newMode = !currentMode
                app.preferences.setManualMarkMode(newMode)
                updateModeUI(newMode)
            }
        }

        binding.deletionTimeContainer.setOnClickListener {
            showDeletionTimeDialog()
        }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                app.preferences.setNotificationsEnabled(isChecked)
            }
        }

        binding.folderPathContainer.setOnClickListener {
            showFolderDialog()
        }
    }

    private fun updateModeUI(isManualMode: Boolean) {
        if (isManualMode) {
            binding.btnToggleMode.text = "Manual"
            binding.deletionTimeContainer.visibility = View.GONE
            binding.modeDescription.text =
                "You'll choose deletion time for each screenshot when it's captured"
        } else {
            binding.btnToggleMode.text = "Automatic"
            binding.deletionTimeContainer.visibility = View.VISIBLE
            binding.modeDescription.text =
                "Screenshots will be automatically deleted after the specified time"
        }
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
            "1 week",
            "Custom..."
        )

        val values = longArrayOf(
            TimeUnit.MINUTES.toMillis(FIVE_MINUTES),
            TimeUnit.MINUTES.toMillis(FIFTEEN_MINUTES),
            TimeUnit.MINUTES.toMillis(THIRTY_MINUTES),
            TimeUnit.HOURS.toMillis(ONE_HOUR),
            TimeUnit.HOURS.toMillis(TWO_HOURS),
            TimeUnit.HOURS.toMillis(SIX_HOURS),
            TimeUnit.HOURS.toMillis(TWELVE_HOURS),
            TimeUnit.DAYS.toMillis(ONE_DAY),
            TimeUnit.DAYS.toMillis(THREE_DAYS),
            TimeUnit.DAYS.toMillis(SEVEN_DAYS)
        )

        AlertDialog.Builder(this)
            .setTitle("Select Deletion Time")
            .setItems(options) { _, which ->
                if (which == options.size - 1) {
                    showCustomTimeDialog()
                } else {
                    lifecycleScope.launch {
                        app.preferences.setDeletionTimeMillis(values[which])
                        binding.deletionTimeText.text = options[which]
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomTimeDialog() {
        val timeUnits = arrayOf("Minutes", "Hours", "Days")
        val inputLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(PADDING_HORIZONTAL, PADDING_TOP, PADDING_HORIZONTAL, PADDING_BOTTOM)
        }

        val valueInput = android.widget.EditText(this).apply {
            hint = "Enter time value"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(DEFAULT_TIME_VALUE.toString())
        }

        val unitSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                timeUnits
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        inputLayout.addView(android.widget.TextView(this).apply {
            text = "Time value:"
            setPadding(0, 0, 0, PADDING_SMALL)
        })
        inputLayout.addView(valueInput)
        inputLayout.addView(android.widget.TextView(this).apply {
            text = "Time unit:"
            setPadding(0, PADDING_MEDIUM, 0, PADDING_SMALL)
        })
        inputLayout.addView(unitSpinner)

        AlertDialog.Builder(this)
            .setTitle("Custom Deletion Time")
            .setView(inputLayout)
            .setPositiveButton("Set") { _, _ ->
                val value = valueInput.text.toString().toIntOrNull() ?: DEFAULT_TIME_VALUE
                val unit = unitSpinner.selectedItemPosition

                val millis = when (unit) {
                    UNIT_MINUTES -> TimeUnit.MINUTES.toMillis(value.toLong())
                    UNIT_HOURS -> TimeUnit.HOURS.toMillis(value.toLong())
                    UNIT_DAYS -> TimeUnit.DAYS.toMillis(value.toLong())
                    else -> TimeUnit.MINUTES.toMillis(value.toLong())
                }

                lifecycleScope.launch {
                    app.preferences.setDeletionTimeMillis(millis)
                    binding.deletionTimeText.text = formatDeletionTime(millis)
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
