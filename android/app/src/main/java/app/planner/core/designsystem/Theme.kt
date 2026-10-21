package app.planner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object Palette {
    val Ivory = Color(0xFFFAF8F4)
    val Charcoal = Color(0xFF092C24)
    val Teal = Color(0xFF07513B)
    val Mint = Color(0xFFE7F3ED)
    val Sand = Color(0xFFFFF4D9)
    val Apricot = Color(0xFFFFF0D4)
    val Orange = Color(0xFFBD4D0B)
    val Idea = Color(0xFFEAEBFF)
    val Violet = Color(0xFF4353F4)
    val Secondary = Color(0xFF637873)
}
@Composable fun PlannerTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = appearance == "dark" || (appearance != "light" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFFA8D8C3), onPrimary = Color(0xFF103D2D), secondary = Color(0xFFE7CD95), tertiary = Color(0xFFBEC4FF), primaryContainer = Color(0xFF244337), secondaryContainer = Color(0xFF423B28), tertiaryContainer = Color(0xFF303450), onPrimaryContainer = Color(0xFFE7F3ED), onSecondaryContainer = Color(0xFFFFF0D4), onTertiaryContainer = Color(0xFFE9EBFF), background = Color(0xFF17241F), surface = Color(0xFF17241F), surfaceContainerLow = Color(0xFF22332B), surfaceContainerLowest = Color(0xFF22332B), onSurface = Color(0xFFEAF3ED), onSurfaceVariant = Color(0xFFB1C4BB), outlineVariant = Color(0xFF3C5046), error = Color(0xFFE7CD95)) else lightColorScheme(primary = Palette.Teal, onPrimary = Color.White, secondary = Palette.Orange, tertiary = Palette.Violet, outline = Color(0xFFA4B8AD), surfaceContainerHighest = Color(0xFFEDF2ED), background = Palette.Ivory, onBackground = Palette.Charcoal, surface = Palette.Ivory, surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Palette.Mint, onSurface = Palette.Charcoal, onSurfaceVariant = Palette.Secondary, primaryContainer = Palette.Mint, onPrimaryContainer = Palette.Charcoal, secondaryContainer = Palette.Sand, onSecondaryContainer = Color(0xFF715616), tertiaryContainer = Palette.Idea, onTertiaryContainer = Color(0xFF182547), outlineVariant = Color(0xFFE6EBE5), error = Palette.Secondary)
    MaterialTheme(colorScheme = colors, typography = PlannerTypography) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() }
    }
}
