package ro.snapify.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import ro.snapify.R
import ro.snapify.ui.theme.ErrorRed
import ro.snapify.ui.theme.WarningOrange
import ro.snapify.util.PermissionUtils.updatePermissionStatuses

/**
 * Permission request dialog with tabs for required and optional permissions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDialog(
    onDismiss: () -> Unit,
    onPermissionsUpdated: (() -> Unit)? = null,
    autoCloseWhenGranted: Boolean = true
) {
    val context = LocalContext.current
    var permissionStatuses by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var permanentlyDeniedPermissions by remember { mutableStateOf(setOf<String>()) }

    // Define permissions
    val readPerm =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    val requiredPermissions: List<Pair<String, String>> = listOfNotNull(
        "Read Screenshots" to readPerm,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "All Files Access" to "manage" else null,
        "Overlay Permission" to "overlay",
        "Battery Optimization" to "battery"
    )

    val optionalPermissions: List<Pair<String, String>> = listOfNotNull(
        @Suppress("NewApi") "Notifications" to Manifest.permission.POST_NOTIFICATIONS
    ).filter { it.second != Manifest.permission.POST_NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    // Launcher for standard permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStatuses(
            context,
            (requiredPermissions + optionalPermissions).map { it.second }) { statusMap ->
            permissionStatuses = statusMap
            // Check which permissions were denied and might be permanently denied
            results.forEach { (permission, granted) ->
                if (!granted && !statusMap[permission]!!) {
                    permanentlyDeniedPermissions = permanentlyDeniedPermissions + permission
                }
            }
        }
        onPermissionsUpdated?.invoke()
    }

    // Launcher for special permissions
    val specialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        updatePermissionStatuses(
            context,
            (requiredPermissions + optionalPermissions).map { it.second }) { statusMap ->
            permissionStatuses = statusMap
        }
        onPermissionsUpdated?.invoke()
    }

    // Update statuses
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val allPermissions = (requiredPermissions + optionalPermissions).map { it.second }
        updatePermissionStatuses(
            context,
            allPermissions
        ) { statusMap -> permissionStatuses = statusMap }
    }

    // Check if all required permissions are already granted and auto-close if so (only for main screen usage)
    androidx.compose.runtime.LaunchedEffect(permissionStatuses) {
        if (autoCloseWhenGranted && permissionStatuses.isNotEmpty()) {
            val requiredGranted = requiredPermissions.all { permissionStatuses[it.second] == true }
            if (requiredGranted) {
                // All required permissions granted, refresh monitoring status and close dialog
                onPermissionsUpdated?.invoke()
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val isOLED = MaterialTheme.colorScheme.surface == Color.Black
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(
                        width = if (isOLED) 1.dp else 0.dp,
                        color = if (isOLED) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isOLED) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
                androidx.compose.material3.MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_required),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Tab row for Required vs Optional permissions
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text("Required") }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text("Optional") }
                            )
                        }

                        val currentPermissions =
                            if (selectedTabIndex == 0) requiredPermissions else optionalPermissions

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            currentPermissions.forEach { (name, perm) ->
                                val isGranted = permissionStatuses[perm] ?: false
                                val isOptional = selectedTabIndex == 1 // Optional tab
                                val isPermanentlyDenied =
                                    permanentlyDeniedPermissions.contains(perm) && !isGranted
                                PermissionItem(
                                    name = name,
                                    isGranted = isGranted,
                                    isOptional = isOptional,
                                    showSettingsButton = isPermanentlyDenied,
                                    onOpenSettings = if (isPermanentlyDenied) {
                                        {
                                            val intent =
                                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                    .apply {
                                                        data =
                                                            android.net.Uri.parse("package:${context.packageName}")
                                                    }
                                            context.startActivity(intent)
                                        }
                                    } else null,
                                    onClick = {
                                        when (perm) {
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.READ_MEDIA_IMAGES,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            Manifest.permission.POST_NOTIFICATIONS -> {
                                                permissionLauncher.launch(arrayOf(perm))
                                            }

                                            "manage" -> {
                                                @Suppress("NewApi") val intent =
                                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                specialLauncher.launch(intent)
                                            }

                                            "overlay" -> {
                                                val intent =
                                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                                specialLauncher.launch(intent)
                                            }

                                            "battery" -> {
                                                @Suppress("BatteryLife") val intent =
                                                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                        .setData("package:${context.packageName}".toUri())
                                                specialLauncher.launch(intent)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End)
                                .padding(top = 4.dp)
                        ) {
                            Text(text = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual permission item in the permission dialog
 */
@Composable
fun PermissionItem(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    isOptional: Boolean = false,
    showSettingsButton: Boolean = false,
    onOpenSettings: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isGranted -> Color(0xFF1976D2) // Blue when granted
                isOptional && !showSettingsButton -> WarningOrange // Orange for optional not granted
                showSettingsButton -> Color(0xFF9C27B0) // Purple for permanently denied
                else -> ErrorRed // Red for required not granted
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (showSettingsButton && onOpenSettings != null) {
                    TextButton(
                        onClick = onOpenSettings,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open Settings", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = if (isGranted) "✓" else "✗",
                        color = Color.White
                    )
                }
            }
            if (showSettingsButton) {
                Text(
                    text = "Permission permanently denied. Please grant in app settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Debug console filter dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugFilterDialog(
    showFilterDialog: Boolean,
    onDismiss: () -> Unit,
    debugChecked: Boolean,
    onDebugCheckedChange: (Boolean) -> Unit,
    infoChecked: Boolean,
    onInfoCheckedChange: (Boolean) -> Unit,
    warningChecked: Boolean,
    onWarningCheckedChange: (Boolean) -> Unit,
    errorChecked: Boolean,
    onErrorCheckedChange: (Boolean) -> Unit,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    useRegex: Boolean,
    onUseRegexChange: (Boolean) -> Unit
) {
    if (showFilterDialog) {
        val isOLED = MaterialTheme.colorScheme.surface == Color.Black
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Log Filters") },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = if (isOLED) Modifier.border(
                1.dp,
                Color.White,
                RoundedCornerShape(28.dp)
            ) else Modifier,
            text = {
                androidx.compose.material3.MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
                    Column {
                        Text("Log Levels:")
                        Row {
                            Checkbox(
                                checked = debugChecked,
                                onCheckedChange = onDebugCheckedChange
                            )
                            Text("Debug", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row {
                            Checkbox(
                                checked = infoChecked,
                                onCheckedChange = onInfoCheckedChange
                            )
                            Text("Info", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row {
                            Checkbox(
                                checked = warningChecked,
                                onCheckedChange = onWarningCheckedChange
                            )
                            Text("Warning", modifier = Modifier.padding(start = 8.dp))
                        }
                        Row {
                            Checkbox(
                                checked = errorChecked,
                                onCheckedChange = onErrorCheckedChange
                            )
                            Text("Error", modifier = Modifier.padding(start = 8.dp))
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
                        TextField(
                            value = customText,
                            onValueChange = onCustomTextChange,
                            label = { Text("Custom search") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row {
                            Checkbox(checked = useRegex, onCheckedChange = onUseRegexChange)
                            Text("Use regex", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}
