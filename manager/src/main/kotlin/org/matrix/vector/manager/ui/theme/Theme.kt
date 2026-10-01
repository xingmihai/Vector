package org.matrix.vector.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import org.matrix.vector.ui.theme.MiuixVectorTheme
import org.matrix.vector.ui.theme.SeedScheme
import org.matrix.vector.ui.theme.ThemeMode
import org.matrix.vector.ui.theme.toAmoled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.matrix.vector.manager.di.ServiceLocator

@Composable
fun VectorTheme(content: @Composable () -> Unit) {
    val settings = ServiceLocator.settings
    val modeKey by settings.themeMode.collectAsState()
    val dynamicRequested by settings.dynamicColor.collectAsState()
    val amoled by settings.amoledBlack.collectAsState()
    val seed by settings.seedColor.collectAsState()

    val dark =
        when (ThemeMode.from(modeKey)) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }

    // A colour preference is stored as an ARGB int, which is what the picker hands back and
    // what the seed scheme takes. Miuix wants a Color, so the conversion happens once here
    // rather than at the setting.
    val seedColor = remember(seed) { seed?.let { Color(it) } }

    val context = LocalContext.current
    // Dynamic colour is this app's default; the seed below applies before Android 12, or whenever
    // the user would rather choose the colour themselves than inherit their wallpaper's.
    val dynamic = dynamicRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Miuix supplies the palette; Material is derived from it, so screens already written pick
    // the colours up untouched and ones being rewritten can use Miuix components beside them.
    MiuixVectorTheme(
        mode = ThemeMode.from(modeKey),
        // A chosen seed overrides the wallpaper. Null when the reader asked for dynamic colour
        // and the platform can supply it: Miuix then reads the wallpaper itself.
        seed = seedColor,
        amoled = amoled,
    ) { scheme ->
        var colors = scheme
        // Dynamic and seedless is the one combination Miuix resolves against the wallpaper, so
        // the platform's own scheme is the closer answer there.
        if (dynamic && seedColor == null) {
            colors =
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        if (dark && amoled) colors = colors.toAmoled()

        MaterialExpressiveTheme(
            colorScheme = colors,
            // The expressive motion scheme is what makes a state change feel caused rather than
            // scheduled. It drives the status indicator's shape morph and the nav transitions.
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
