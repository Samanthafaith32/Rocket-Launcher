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

/** One entry in the theme picker. */
data class LTheme(
    val id: String,
    val name: String,
    val text: Color,
    val accent: Color,
    val scrim: Color,      // laid over the wallpaper so the text stays readable
    val scrimAlpha: Float,
    val light: Boolean = false,
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
