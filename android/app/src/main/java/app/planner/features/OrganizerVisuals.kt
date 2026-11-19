package app.planner.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavHostController
import app.planner.R
import app.planner.core.designsystem.*

@Composable
internal fun organizerLabel(english: String, russian: String): String =
    if (LocalConfiguration.current.locales[0].language == "ru") russian else english

@Composable
internal fun WarmHeader(title: String, nav: NavHostController, colors: TodayColors, showProfile: Boolean = true, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = TodayType.title.copy(fontSize = 36.sp), color = colors.ink)
        action()
        if (showProfile) IconButton({ nav.navigate("profile") { launchSingleTop = true } }, Modifier.semantics { contentDescription = "Open profile" }) {
            Box(Modifier.size(38.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                PlannerIcon(Glyph.User, Modifier.size(20.dp), colors.ink)
            }
        }
        IconButton({ nav.navigate("settings") }, Modifier.semantics { contentDescription = "Open settings" }) {
            Box(Modifier.size(38.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                PlannerIcon(Glyph.Settings, Modifier.size(21.dp), colors.ink)
            }
        }
    }
}

@Composable
internal fun WarmCat(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.today_cat_peek), null, modifier.width(135.dp).aspectRatio(768f / 591f))
}

@Composable
internal fun WarmPanel(colors: TodayColors, modifier: Modifier = Modifier, lavender: Boolean = false, container: Color = colors.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(container)
            .then(if (lavender) Modifier.todayCollectionArt(colors) else Modifier),
        content = content,
    )
}

@Composable
internal fun WarmChoice(
    label: String, colors: TodayColors, modifier: Modifier = Modifier, selected: Boolean = false,
    click: () -> Unit,
) {
    Box(
        modifier.clip(CircleShape).background(if (selected) colors.surface else colors.peach)
            .clickable(role = Role.Button, onClick = click).heightIn(min = 48.dp).padding(horizontal = 7.dp, vertical = 6.dp)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = colors.ink, style = TodayType.meta, textAlign = TextAlign.Center) }
}
