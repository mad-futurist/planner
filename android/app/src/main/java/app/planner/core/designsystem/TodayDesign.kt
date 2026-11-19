package app.planner.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.planner.R

internal data class TodayColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val checkOutline: Color,
    val peach: Color,
    val peachDeep: Color,
    val amberInk: Color,
    val lavender: Color,
    val lavenderMid: Color,
    val lavenderDeep: Color,
    val completed: Color,
    val composer: Color,
    val composerText: Color,
    val composerMuted: Color,
)

private val LightToday = TodayColors(
    canvas = Color(0xFFFCF6EF), surface = Color(0xFFFFFCF8), ink = Color(0xFF292527),
    muted = Color(0xFF716762), line = Color(0xFFEADFD6), checkOutline = Color(0xFF9D8170),
    peach = Color(0xFFFBE8D7), peachDeep = Color(0xFFF6D4AE), amberInk = Color(0xFF87470E),
    lavender = Color(0xFFEEE3F0), lavenderMid = Color(0xFFDCC5E9), lavenderDeep = Color(0xFFBEA4CB),
    completed = Color(0xFF756B66), composer = Color(0xFF292527), composerText = Color(0xFFFFFCF8),
    composerMuted = Color(0xFFD0C4BD),
)

private val DarkToday = TodayColors(
    canvas = Color(0xFF241E1D), surface = Color(0xFF342B29), ink = Color(0xFFFFF0E6),
    muted = Color(0xFFD3C1B7), line = Color(0xFF5A4B46), checkOutline = Color(0xFF977A6A),
    peach = Color(0xFF503828), peachDeep = Color(0xFF65472F), amberInk = Color(0xFFFFD398),
    lavender = Color(0xFF443445), lavenderMid = Color(0xFF58415D), lavenderDeep = Color(0xFF73577E),
    completed = Color(0xFFC6B7AD), composer = Color(0xFF171314), composerText = Color(0xFFFFF0E6),
    composerMuted = Color(0xFFCCBCB3),
)

@Composable
internal fun plannerDarkAppearance(appearance: String): Boolean =
    appearance == "dark" || (appearance != "light" && isSystemInDarkTheme())

internal fun todayColors(dark: Boolean): TodayColors = if (dark) DarkToday else LightToday

@OptIn(ExperimentalTextApi::class)
private fun todayFont(id: Int, vararg weights: FontWeight) = FontFamily(
    *weights.map { Font(id, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) }.toTypedArray()
)

internal object TodayType {
    private val onest = todayFont(R.font.onest, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    private val manrope = todayFont(R.font.manrope, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    val title = TextStyle(fontFamily = onest, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.3).sp)
    val section = TextStyle(fontFamily = onest, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
    val body = TextStyle(fontFamily = onest, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val meta = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp)
    val nav = meta.copy(fontSize = 11.sp, lineHeight = 15.sp)
}

private fun path(data: String) = PathParser().parsePathString(data).toPath()

internal fun Modifier.todayBackdrop(colors: TodayColors): Modifier = drawWithCache {
    val top = path("M404 9 C326 11 278 32 250 70 C211 121 241 153 277 197 C334 192 369 184 404 151Z")
    val right = path("M410 83 Q323 99 269 153 Q243 202 193 220 Q171 239 169 267 L410 365Z")
    val left = path("M-79 279 Q51 133 97 216 Q146 300 78 335 Q8 365 -83 332Z")
    val bottom = path("M-50 592 Q54 559 139 675 Q232 803 410 625 L410 845 L-50 845Z")
    onDrawBehind {
        scale(size.width / 390f, size.height / 844f, pivot = Offset.Zero) {
            drawPath(top, Brush.linearGradient(listOf(colors.peachDeep, colors.peach.copy(alpha = .25f)), Offset(235f, 45f), Offset(395f, 160f)))
            drawPath(right, Brush.linearGradient(listOf(colors.peachDeep, colors.peach.copy(alpha = .25f)), Offset(375f, 110f), Offset(255f, 355f)))
            drawPath(left, Brush.linearGradient(listOf(colors.peachDeep, colors.peach.copy(alpha = .2f)), Offset(104f, 193f), Offset(12f, 321f)))
            drawPath(bottom, colors.peach.copy(alpha = .55f))
        }
    }
}

internal fun Modifier.todayCollectionArt(colors: TodayColors): Modifier = drawWithCache {
    val wave = path("M208 78 Q258 9 322 -4 L369 -4 L369 78Z")
    val rise = path("M262 78 Q259 43 255 30 Q252 11 258 -5 L369 -5 L369 78Z")
    val hill = path("M257 78 Q284 34 307 78Z")
    onDrawBehind {
        scale(size.width / 360f, size.height / 78f, pivot = Offset.Zero) {
            drawPath(rise, colors.lavender)
            drawPath(wave, colors.lavenderMid.copy(alpha = .66f))
            drawPath(hill, colors.lavenderDeep.copy(alpha = .5f))
            drawLine(colors.ink.copy(alpha = .77f), Offset(225f, 17f), Offset(226f, 27f), 2.7f, StrokeCap.Round)
            drawLine(colors.ink.copy(alpha = .77f), Offset(238f, 19f), Offset(233f, 32f), 2.7f, StrokeCap.Round)
        }
    }
}

internal fun Modifier.todayComposerArt(colors: TodayColors): Modifier = drawWithCache {
    val outer = size.width - 9.dp.toPx()
    val stroke = 2.6.dp.toPx()
    onDrawBehind {
        drawLine(colors.ink.copy(alpha = .76f), Offset(outer - 9.dp.toPx(), 2.dp.toPx()), Offset(outer - 14.dp.toPx(), 10.dp.toPx()), stroke, StrokeCap.Round)
        drawLine(colors.ink.copy(alpha = .76f), Offset(outer, 13.dp.toPx()), Offset(outer - 9.dp.toPx(), 17.dp.toPx()), stroke, StrokeCap.Round)
    }
}
