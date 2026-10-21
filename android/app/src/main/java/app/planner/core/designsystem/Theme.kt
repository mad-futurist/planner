package app.planner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ivory = Color(0xFFFAF8F4)
    val Charcoal = Color(0xFF232D2B)
    val Teal = Color(0xFF315E53)
    val Mint = Color(0xFFEAF2ED)
    val Sand = Color(0xFFF4EEDD)
    val Idea = Color(0xFFE8F0F0)
    val Secondary = Color(0xFF69736F)
}
@Composable fun PlannerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFA8CDBD), secondary = Color(0xFFA8CDBD), tertiary = Color(0xFFA8CDBD), primaryContainer = Color(0xFF203E34), secondaryContainer = Color(0xFF39372F), tertiaryContainer = Color(0xFF233C3C), onPrimaryContainer = Color(0xFFE8EDE8), onSecondaryContainer = Color(0xFFE8EDE8), onTertiaryContainer = Color(0xFFE8EDE8), background = Color(0xFF17211E), surface = Color(0xFF202C27), onSurface = Color(0xFFE8EDE8), error = Color(0xFFCCD3CA)) else lightColorScheme(primary = Palette.Teal, secondary = Palette.Teal, tertiary = Palette.Teal, background = Palette.Ivory, surface = Palette.Ivory, surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Palette.Mint, onSurface = Palette.Charcoal, onSurfaceVariant = Palette.Secondary, primaryContainer = Palette.Mint, onPrimaryContainer = Palette.Charcoal, secondaryContainer = Palette.Sand, onSecondaryContainer = Palette.Charcoal, tertiaryContainer = Palette.Idea, onTertiaryContainer = Palette.Charcoal, outlineVariant = Color(0xFFE1E6E0), error = Palette.Secondary)
    MaterialTheme(colorScheme = colors, content = content)
}
