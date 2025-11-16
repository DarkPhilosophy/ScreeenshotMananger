package ro.snapify.ui

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ro.snapify.data.preferences.AppPreferences
import ro.snapify.ui.components.PermissionDialog
import ro.snapify.ui.theme.AppTheme
import ro.snapify.ui.theme.ThemeMode
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: AppPreferences

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle language changes
        lifecycleScope.launch {
            preferences.language.collect { language ->
                val localeTag = if (language == "ro") "ro" else "en"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = getSystemService(LocaleManager::class.java)
                    localeManager?.applicationLocales = LocaleList.forLanguageTags(localeTag)
                } else {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(localeTag)
                    )
                }
            }
        }

        // Handle auto cleanup worker
        lifecycleScope.launch {
            preferences.autoCleanupEnabled.collect { enabled ->
                val workManager = WorkManager.getInstance(this@MainActivity)
                if (enabled) {
                    val constraints = Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                    val request =
                        PeriodicWorkRequestBuilder<ro.snapify.worker.AutoCleanupWorker>(
                            24,
                            java.util.concurrent.TimeUnit.HOURS
                        )
                            .setConstraints(constraints)
                            .build()
                    workManager.enqueueUniquePeriodicWork(
                        "auto_cleanup",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
                } else {
                    workManager.cancelUniqueWork("auto_cleanup")
                }
            }
        }

        setContent {
            val themeModeString by preferences.themeMode.collectAsState(initial = "system")
            val themeMode = when (themeModeString) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "dynamic" -> ThemeMode.DYNAMIC
                "oled" -> ThemeMode.OLED
                else -> ThemeMode.SYSTEM
            }

            var uiState by remember { mutableStateOf(MainUiState()) }
            LaunchedEffect(Unit) {
                viewModel.uiState.collect { uiState = it }
            }

            // Add default folder if none configured and not initialized
            LaunchedEffect(Unit) {
                val hasInit = preferences.hasInitializedFolders.first()
                if (!hasInit) {
                    val current = preferences.mediaFolderUris.first()
                    if (current.isEmpty()) {
                        val defaultUri =
                            "content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreenshots"
                        preferences.setMediaFolderUris(setOf(defaultUri))
                    }
                    preferences.setHasInitializedFolders(true)
                }
            }

            AppTheme(themeMode = themeMode) {
                var isDrawerOpen by remember { mutableStateOf(false) }

                val fabX by animateDpAsState(
                    targetValue = if (isDrawerOpen) (LocalConfiguration.current.screenWidthDp.dp - 72.dp) else 16.dp,
                    animationSpec = tween(durationMillis = 600)
                )

                BackHandler(enabled = isDrawerOpen) {
                    isDrawerOpen = false
                }

                DuoDrawer(
                    isOpen = isDrawerOpen,
                    onOpenDrawer = { isDrawerOpen = true },
                    onCloseDrawer = { isDrawerOpen = false },
                    showDialog = uiState.showPermissionDialog,
                    menuContent = { drawerOpen ->
                        MenuContent(
                            isOpen = drawerOpen,
                            onHomeClick = { isDrawerOpen = false },
                            onCloseDrawer = { isDrawerOpen = false },
                            isDrawerOpen = isDrawerOpen,
                            preferences = preferences,
                            mainViewModel = viewModel,
                            dialogContent = {}
                        )
                    },
                    content = {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenDrawer = { isDrawerOpen = true },
                            preferences = preferences,
                            isDrawerOpen = isDrawerOpen
                        )
                    },
                    dialogContent = {
                        if (uiState.showPermissionDialog) {
                            PermissionDialog(
                                onDismiss = { viewModel.hidePermissionsDialog() },
                                onPermissionsUpdated = {
                                    // Start the service when permissions are granted
                                    val intent = android.content.Intent(
                                        this@MainActivity,
                                        ro.snapify.service.ScreenshotMonitorService::class.java
                                    )
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        this@MainActivity.startForegroundService(intent)
                                    } else {
                                        this@MainActivity.startService(intent)
                                    }
                                    viewModel.refreshMonitoringStatus()
                                },
                                autoCloseWhenGranted = true
                            )
                        }
                    }
                )

                // FAB for drawer toggle
                FloatingActionButton(
                    onClick = { isDrawerOpen = !isDrawerOpen },
                    modifier = Modifier.offset(
                        x = fabX,
                        y = 40.dp
                    ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Crossfade(targetState = isDrawerOpen) { open ->
                        Icon(
                            imageVector = if (open) Icons.AutoMirrored.Filled.Shortcut else Icons.AutoMirrored.Filled.List,
                            contentDescription = if (open) "Close drawer" else "Open drawer"
                        )
                    }
                }

                // Animated Settings title with custom letter-by-letter fade animation
                if (isDrawerOpen) {
                    val textXAnimatable = remember { Animatable(fabX.value + 56.dp.value) }

                    // After FAB animation completes, start text animation and slide
                    LaunchedEffect(isDrawerOpen) {
                        if (isDrawerOpen) {
                            // Wait for FAB animation to complete, then slide text leftward
                            kotlinx.coroutines.delay(650) // Match FAB animation timing
                            textXAnimatable.animateTo(
                                targetValue = 16.dp.value,
                                animationSpec = tween(durationMillis = 600, easing = EaseOutCubic)
                            )
                        }
                    }

                    // Custom letter-by-letter fade animation
                    val text = "Settings"
                    val letterAlphas = remember { List(text.length) { Animatable(0f) } }

                    // Animate each letter with staggered delay
                    LaunchedEffect(isDrawerOpen) {
                        if (isDrawerOpen) {
                            kotlinx.coroutines.delay(650) // Start after FAB animation
                            letterAlphas.forEachIndexed { index, animatable ->
                                animatable.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        delayMillis = index * 80
                                    )
                                )
                            }
                        }
                    }

                    // Render each letter with its own alpha
                    Row(
                        modifier = Modifier
                            .offset(
                                x = textXAnimatable.value.dp,
                                y = 48.dp
                            )
                    ) {
                        text.forEachIndexed { index, char ->
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.alpha(letterAlphas[index].value)
                            )
                        }
                    }
                }
            }
        }

        // Broadcast receiver removed - use refresh button for now

        // Handle first launch
        // lifecycleScope.launch {
        //     val isFirstLaunch = preferences.isFirstLaunch.first()
        //     if (isFirstLaunch) {
        //         // Show welcome dialog or handle first launch
        //         preferences.setFirstLaunch(false)
        //     }
        // }
    }
}
