package org.matrix.vector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDark
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLight

/**
 * Miuix, as the manager's outer theme, with Material kept alive underneath it.
 *
 * The manager is written against Material 3 and will be for a while — a hundred files do not
 * become Miuix in one pass. So this is not a replacement but a bridge: Miuix supplies the
 * colours, and Material is re-derived from them, which means a screen already written gets the
 * new palette for free and one being rewritten can reach for Miuix components directly.
 *
 * Both themes are present rather than one standing in for the other because the two libraries
 * each read their own: Miuix components resolve `MiuixTheme.colorScheme`, Material ones
 * `MaterialTheme.colorScheme`, and a screen mid-migration holds both kinds at once.
 */
@Composable
fun MiuixVectorTheme(
    mode: ThemeMode = ThemeMode.System,
    seed: Color? = null,
    amoled: Boolean = false,
    typography: Typography = Typography(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Monet when a seed was picked, and the library's own schemes when none was. Miuix reads the
    // wallpaper itself in its Monet modes, so no seed is needed to ask for them — but an
    // explicit one has to win, and it is the key colour that says so.
    val schemeMode =
        if (seed != null) {
            if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight
        } else {
            ColorSchemeMode.System
        }

    val controller =
        remember(mode, seed, dark) {
            ThemeController(
                colorSchemeMode = schemeMode,
                keyColor = seed,
                isDark = dark,
            )
        }

    val miuixColors = controller.currentColors()

    // Material, rebuilt out of Miuix's roles. The mapping is deliberately the plain one: Miuix
    // names most of what Material names, and where it does not — `surfaceVariant` has no
    // Material twin — the closest Miuix surface stands in, rather than a value invented here
    // that would drift from both libraries.
    val materialColors =
        remember(miuixColors, dark) {
            val c = miuixColors
            val base =
                if (amoled && dark) {
                    darkColorScheme(
                        primary = c.primary,
                        onPrimary = c.onPrimary,
                        primaryContainer = c.primaryContainer,
                        onPrimaryContainer = c.onPrimaryContainer,
                        secondary = c.secondary,
                        onSecondary = c.onSecondary,
                        secondaryContainer = c.secondaryContainer,
                        onSecondaryContainer = c.onSecondaryContainer,
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = c.surfaceVariant,
                        onSurface = c.onSurface,
                        onBackground = c.onBackground,
                        error = c.error,
                        onError = c.onError,
                        outline = c.outline,
                        surfaceContainer = c.surfaceContainer,
                        surfaceContainerHigh = c.surfaceContainerHigh,
                        surfaceContainerHighest = c.surfaceContainerHighest,
                    )
                } else if (dark) {
                    darkColorScheme(
                        primary = c.primary,
                        onPrimary = c.onPrimary,
                        primaryContainer = c.primaryContainer,
                        onPrimaryContainer = c.onPrimaryContainer,
                        secondary = c.secondary,
                        onSecondary = c.onSecondary,
                        secondaryContainer = c.secondaryContainer,
                        onSecondaryContainer = c.onSecondaryContainer,
                        background = c.background,
                        surface = c.surface,
                        surfaceVariant = c.surfaceVariant,
                        onSurface = c.onSurface,
                        onBackground = c.onBackground,
                        error = c.error,
                        onError = c.onError,
                        outline = c.outline,
                        surfaceContainer = c.surfaceContainer,
                        surfaceContainerHigh = c.surfaceContainerHigh,
                        surfaceContainerHighest = c.surfaceContainerHighest,
                    )
                } else {
                    lightColorScheme(
                        primary = c.primary,
                        onPrimary = c.onPrimary,
                        primaryContainer = c.primaryContainer,
                        onPrimaryContainer = c.onPrimaryContainer,
                        secondary = c.secondary,
                        onSecondary = c.onSecondary,
                        secondaryContainer = c.secondaryContainer,
                        onSecondaryContainer = c.onSecondaryContainer,
                        background = c.background,
                        surface = c.surface,
                        surfaceVariant = c.surfaceVariant,
                        onSurface = c.onSurface,
                        onBackground = c.onBackground,
                        error = c.error,
                        onError = c.onError,
                        outline = c.outline,
                        surfaceContainer = c.surfaceContainer,
                        surfaceContainerHigh = c.surfaceContainerHigh,
                        surfaceContainerHighest = c.surfaceContainerHighest,
                    )
                }
            base
        }

    MiuixTheme(controller = controller) {
        MaterialTheme(colorScheme = materialColors, typography = typography, content = content)
    }
}
