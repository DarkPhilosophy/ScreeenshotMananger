package ro.snapify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController

enum class ThemeMode {
    LIGHT, DARK, SYSTEM, DYNAMIC, OLED
}

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

private val OledColors = darkColorScheme(
    primary = Color(0xFFFFFFFF), // Pure white
    onPrimary = Color(0xFF000000), // Black
    primaryContainer = Color(0xFF000000), // Pure black
    onPrimaryContainer = Color(0xFFFFFFFF), // White
    secondary = Color(0xFFB0B0B0), // Light gray
    onSecondary = Color(0xFF000000), // Black
    secondaryContainer = Color(0xFF000000), // Pure black
    onSecondaryContainer = Color(0xFFFFFFFF), // White
    tertiary = Color(0xFFB0B0B0), // Light gray
    onTertiary = Color(0xFF000000), // Black
    tertiaryContainer = Color(0xFF000000), // Pure black
    onTertiaryContainer = Color(0xFFFFFFFF), // White
    error = Color(0xFFFF4444), // Red
    errorContainer = Color(0xFF000000), // Pure black
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFFFF4444), // Red
    background = Color(0xFF000000), // Pure black
    onBackground = Color(0xFFFFFFFF), // White
    surface = Color(0xFF000000), // Pure black
    onSurface = Color(0xFFFFFFFF), // White
    surfaceVariant = Color(0xFF1A1A1A), // Dark gray for contrast
    onSurfaceVariant = Color(0xFFB0B0B0), // Light gray
    outline = Color(0xFFFFFFFF), // White
    inverseOnSurface = Color(0xFF000000), // Black
    inverseSurface = Color(0xFFFFFFFF), // White
    inversePrimary = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFFFFFFF), // White
    outlineVariant = Color(0xFFFFFFFF), // White
    scrim = Color(0xFF000000), // Black
)

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    skipWindowSetup: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.SYSTEM -> {
            val darkTheme = isSystemInDarkTheme()
            if (darkTheme) DarkColors else LightColors
        }

        ThemeMode.DYNAMIC -> {
            val darkTheme = isSystemInDarkTheme()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColors else LightColors
            }
        }

        ThemeMode.OLED -> OledColors
    }

    if (!skipWindowSetup) {
        val view = LocalView.current

        @Suppress("DEPRECATION")
        val systemUiController = rememberSystemUiController()
        val darkTheme = isSystemInDarkTheme()
        val useDarkIcons = when (themeMode) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK, ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> !darkTheme
            ThemeMode.OLED -> false // OLED has black backgrounds, so use light icons
        }

        SideEffect {
            val context = view.context
            if (context is Activity) {
                val window = context.window
                WindowCompat.setDecorFitsSystemWindows(window, false)

                // Set window background to match theme background
                window.setBackgroundDrawable(colorScheme.background.toArgb().toDrawable())

                // Update system bars to match theme
                systemUiController.setSystemBarsColor(
                    color = colorScheme.surface,
                    darkIcons = useDarkIcons
                )
                systemUiController.setNavigationBarColor(
                    color = colorScheme.surface,
                    darkIcons = useDarkIcons
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
