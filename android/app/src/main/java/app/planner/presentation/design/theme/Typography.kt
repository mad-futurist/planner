package app.planner.presentation.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import app.planner.R

// SIL OFL 1.1; full license texts are in android/licenses.
@OptIn(ExperimentalTextApi::class)
val DisplayFont = FontFamily(Font(R.font.playfair_display, FontWeight.Normal, variationSettings=FontVariation.Settings(FontVariation.weight(400))), Font(R.font.playfair_display, FontWeight.Bold, variationSettings=FontVariation.Settings(FontVariation.weight(750))))

@OptIn(ExperimentalTextApi::class)
val BodyFont = FontFamily(*listOf(FontWeight.Normal,FontWeight.Medium,FontWeight.SemiBold,FontWeight.Bold).map { Font(R.font.dm_sans,it,variationSettings=FontVariation.Settings(FontVariation.weight(it.weight),FontVariation.opticalSizing(18.sp))) }.toTypedArray())

val PlannerTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-1).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontSize = 10.sp, lineHeight = 14.sp),
)
