package app.planner.presentation.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.PathParser
import app.planner.presentation.design.theme.Palette

/** Original outline vocabulary; no icon font or bitmap dependency. */
enum class Glyph(val path: String) {
    Home("M3 10 L12 3 L21 10 L21 21 L15 21 L15 14 L9 14 L9 21 L3 21 Z"),
    Calendar("M5 5 L19 5 Q21 5 21 7 L21 20 Q21 22 19 22 L5 22 Q3 22 3 20 L3 7 Q3 5 5 5 M3 10 L21 10 M8 2 L8 7 M16 2 L16 7"),
    Check("M5 12 L10 17 L20 6"), Plus("M12 4 L12 20 M4 12 L20 12"),
    Tasks("M8 5 L21 5 M8 12 L21 12 M8 19 L21 19 M2 5 L3 6 L5 3 M2 12 L3 13 L5 10 M2 19 L3 20 L5 17"),
    Bulb("M8 17 C8 13 5 13 5 9 C5 0 19 0 19 9 C19 13 16 13 16 17 Z M9 20 L15 20 M10 23 L14 23 M12 16 L12 10"),
    Sparkles("M12 2 Q13 10 21 11 Q13 12 12 21 Q11 13 3 11 Q11 10 12 2 Z M21 1 L21 5 M19 3 L23 3"),
    User("M8 7 A4 4 0 1 0 16 7 A4 4 0 1 0 8 7 M4 22 L4 19 C4 12 20 12 20 19 L20 22"),
    More("M12 4 L12 4.2 M12 12 L12 12.2 M12 20 L12 20.2"),
    Right("M9 4 L17 12 L9 20"), Down("M5 9 L12 16 L19 9"), Left("M15 4 L7 12 L15 20"), Up("M5 15 L12 8 L19 15"),
    ArrowUp("M12 21 L12 3 M5 10 L12 3 L19 10"), Close("M5 5 L19 19 M19 5 L5 19"),
    Clock("M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 M12 7 L12 12 L16 15"),
    Phone("M7 3 L3 5 C1 13 11 23 19 21 L21 17 L16 14 L14 17 Q7 14 7 8 L10 7 Z"),
    Pin("M8 3 L18 3 M10 3 L10 9 L6 14 L18 14 L14 9 L14 3 M12 14 L12 23"),
    Star("M12 2 L15 8 L22 9 L17 14 L18 21 L12 18 L6 21 L7 14 L2 9 L9 8 Z"),
    Shield("M12 2 L21 6 L20 14 Q18 20 12 23 Q6 20 4 14 L3 6 Z"),
    Bell("M5 17 L19 17 L17 13 L17 8 C17 1 7 1 7 8 L7 13 Z M10 21 L14 21"),
    Book("M3 3 Q8 1 12 5 Q16 1 21 3 L21 21 Q16 19 12 23 Q8 19 3 21 Z M12 5 L12 23"),
    Tag("M3 3 L12 3 L22 13 L13 22 L3 12 Z M7 7 L7.1 7"),
    Leaf("M12 23 L12 10 M12 16 Q0 18 2 5 Q12 5 12 16 M12 12 Q22 13 23 1 Q12 1 12 12"),
    Download("M12 2 L12 17 M5 10 L12 17 L19 10 M3 17 L3 22 L21 22 L21 17"),
    Palette("M12 2 C-1 2 -1 22 11 22 C17 22 11 17 15 15 C27 17 25 2 12 2 M7 7 L7.1 7 M13 5 L13.1 5 M18 8 L18.1 8"),
    Sun("M12 7 A5 5 0 1 0 12 17 A5 5 0 1 0 12 7 M12 0 L12 3 M12 21 L12 24 M0 12 L3 12 M21 12 L24 12 M3 3 L5 5 M19 19 L21 21 M3 21 L5 19 M19 5 L21 3"),
    Moon("M20 16 C8 18 6 6 10 2 C-3 7 3 25 16 21 Z"),
    Video("M3 5 L15 5 L15 19 L3 19 Z M15 9 L22 5 L22 19 L15 15"),
    Location("M12 23 C10 20 4 14 4 9 C4 -1 20 -1 20 9 C20 14 14 20 12 23 Z M9 9 A3 3 0 1 0 15 9 A3 3 0 1 0 9 9"),
}

@Composable fun PlannerIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, filled: Boolean = false) {
    val path = remember(glyph) { PathParser().parsePathString(glyph.path).toPath() }
    Canvas(modifier) { scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) { drawPath(path, tint, style = if (filled) Fill else Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
}
