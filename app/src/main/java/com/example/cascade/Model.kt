package com.example.cascade

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.text.Normalizer

/** Letters on the right-hand rail. Apps that don't start with A–Z go under '#'. */
val RailLetters: List<Char> = ('A'..'Z').toList() + '#'

data class AppInfo(
    val id: String, // "package/activity" – unique per launcher entry
    val pkg: String,
    val cls: String,
    val label: String,
    val icon: ImageBitmap,
) {
    val letter: Char = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}

/** How the clock block in the top-left corner is laid out. */
enum class ClockStyle {
    Classic, // big time, long date underneath
    Compact, // big time, "Thu, Aug 30 · 100%" underneath
    Day,     // big weekday ("Thu."), "2:00 · Aug 30 · 100%" underneath
}

/** What sits to the left of each app name (when "Show app icons" is on). */
enum class IconStyle {
    App,    // the app's own icon
    Circle, // the app's icon on a tinted circle
    Dot,    // a small round bullet
}

/** One entry in the theme picker. */
data class LTheme(
    val id: String,
    val name: String,
    val text: Color,
    val accent: Color,
    val scrim: Color,      // laid over the wallpaper so the text stays readable
    val scrimAlpha: Float,
    val light: Boolean = false,
    val backdrop: List<Color>? = null, // optional top-to-bottom gradient painted instead of the wallpaper
    val clock: ClockStyle = ClockStyle.Classic,
    val clockFont: FontFamily = FontFamily.Default,
    val font: FontFamily = FontFamily.Default,
    val icons: IconStyle = IconStyle.App,
)

private val Presets = listOf(
    LTheme("midnight", "Midnight", Color.White, Color(0xFF8AB4F8), Color.Black, 0.30f),
    LTheme("light", "Light", Color(0xFF15181D), Color(0xFF1A73E8), Color.White, 0.40f, light = true),
    LTheme("amoled", "AMOLED", Color.White, Color(0xFF00E5A0), Color.Black, 1f),
    LTheme("ocean", "Ocean", Color(0xFFE6F9FF), Color(0xFF4DD0E1), Color(0xFF00242E), 0.50f),
    LTheme("sunset", "Sunset", Color(0xFFFFF1E6), Color(0xFFFF8A65), Color(0xFF3B1608), 0.45f),
    LTheme("forest", "Forest", Color(0xFFEAF7EC), Color(0xFF81C784), Color(0xFF0B2713), 0.50f),
    LTheme("rose", "Rose", Color(0xFFFFEFF5), Color(0xFFF48FB1), Color(0xFF3A0F20), 0.45f),
    LTheme("violet", "Violet", Color(0xFFF5EBFF), Color(0xFFB388FF), Color(0xFF1D0F36), 0.50f),

    // Themes that bring their own gradient background, clock layout and icon style.
    LTheme(
        "sky", "Sky", Color.White, Color(0xFFBFE3FF), Color(0xFF0B2A4A), 0.12f,
        backdrop = listOf(Color(0xFF3C7DC4), Color(0xFF2A63A8), Color(0xFF1B4E8C)),
        clock = ClockStyle.Compact, font = FontFamily.SansSerif, icons = IconStyle.Circle,
    ),
    LTheme(
        "dusk", "Dusk", Color(0xFFFFEDE0), Color(0xFFFFB68A), Color.Black, 0.10f,
        backdrop = listOf(Color(0xFF3F2E9A), Color(0xFF1A1245), Color(0xFF080620)),
        clock = ClockStyle.Day, clockFont = FontFamily.SansSerif, font = FontFamily.SansSerif,
    ),
    LTheme(
        "dune", "Dune", Color(0xFFF0F0F0), Color(0xFFBDBDBD), Color.Black, 0.10f,
        backdrop = listOf(Color(0xFF5E5E5E), Color(0xFF2A2A2A), Color(0xFF0C0C0C)),
        clock = ClockStyle.Compact, font = FontFamily.SansSerif, icons = IconStyle.Dot,
    ),
    LTheme(
        "cloud", "Cloud", Color(0xFF10243A), Color(0xFF2477C9), Color.White, 0.12f, light = true,
        backdrop = listOf(Color(0xFF9FD0F5), Color(0xFFEAF5FF)),
        clock = ClockStyle.Day, clockFont = FontFamily.Cursive, font = FontFamily.SansSerif,
        icons = IconStyle.Circle,
    ),
    LTheme(
        "ember", "Ember", Color(0xFFFFF4EC), Color(0xFFFFC2B0), Color.Black, 0.10f,
        backdrop = listOf(Color(0xFF5C2A6B), Color(0xFFC4473F), Color(0xFF7A2230)),
        clock = ClockStyle.Day, clockFont = FontFamily.Cursive, font = FontFamily.SansSerif,
        icons = IconStyle.Circle,
    ),
)

/** Auto (follows system dark/light) + presets + Material You on Android 12+. */
@Composable
fun themeList(): List<LTheme> {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        val auto = (if (dark) Presets[0] else Presets[1]).copy(id = "auto", name = "Auto")
        val dynamic = if (Build.VERSION.SDK_INT >= 31) {
            val cs = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            listOf(LTheme("dynamic", "Dynamic", cs.onSurface, cs.primary, cs.surface, 0.55f, light = !dark))
        } else {
            emptyList()
        }
        listOf(auto) + Presets + dynamic
    }
}
