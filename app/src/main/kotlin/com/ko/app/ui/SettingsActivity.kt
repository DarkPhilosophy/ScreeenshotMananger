package com.ko.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ko.app.BuildConfig
import com.ko.app.R
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

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            handleFolderSelection(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        app = application as ScreenshotApp

        setupToolbar()
        loadSettings()
        setupListeners()
        setupAboutSection()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupAboutSection() {
        binding.versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        
        binding.emailText.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:ualexen92@gmail.com".toUri()
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
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

            val currentFolder = app.preferences.screenshotFolder.first()
            if (currentFolder.isEmpty()) {
                binding.folderPathText.text = getString(R.string.default_folder)
            } else {
            val uri = android.net.Uri.parse(currentFolder)
            val folderPath = uri.path?.substringAfter(":")?.let {
                if (it.startsWith("/")) it.substring(1) else it
            } ?: currentFolder
            binding.folderPathText.text = folderPath
            }
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

        binding.btnOpenDebugConsole.setOnClickListener {
            startActivity(Intent(this, DebugConsoleActivity::class.java))
        }
    }

    private fun updateModeUI(isManualMode: Boolean) {
        if (isManualMode) {
        binding.btnToggleMode.text = getString(R.string.manual_mode)
        binding.deletionTimeContainer.visibility = View.GONE
        binding.modeDescription.text = getString(R.string.manual_description)
        } else {
            binding.btnToggleMode.text = getString(R.string.automatic_mode)
        binding.deletionTimeContainer.visibility = View.VISIBLE
        binding.modeDescription.text = getString(R.string.automatic_description)
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
            .setTitle(getString(R.string.select_deletion_time))
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
            hint = getString(R.string.enter_time_value)
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
            text = getString(R.string.time_unit)
            setPadding(0, PADDING_MEDIUM, 0, PADDING_SMALL)
        })
        inputLayout.addView(unitSpinner)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_deletion_time_title))
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
        folderPickerLauncher.launch(null)
    }

    private fun handleFolderSelection(uri: Uri) {
        try {
            // Persist URI permissions
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // Extract folder path for display
            val folderPath = uri.path?.substringAfter(":")?.let {
                if (it.startsWith("/")) it.substring(1) else it
            } ?: uri.toString()

            // Update UI immediately
            binding.folderPathText.text = folderPath

            // Save folder URI to preferences
            lifecycleScope.launch {
                app.preferences.setScreenshotFolder(uri.toString())
            }

            // Show success message
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.folder_selected))
                .setMessage(getString(R.string.folder_updated_message, folderPath))
                .setPositiveButton("OK", null)
                .show()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to handle folder selection", e)
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to select folder. Please try again.")
                .setPositiveButton("OK", null)
                .show()
        }
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
