package app.planner.presentation.today

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.presentation.common.ScreenHeader
import app.planner.presentation.common.ScreenPadding
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.PlannerIcon
import app.planner.presentation.navigation.PlannerRoutes
import app.planner.presentation.navigation.ScreenNavigation
import app.planner.presentation.today.components.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun TodayScreen(state:TodayUiState,actions:TodayActions,nav:ScreenNavigation,status:String,edit:(String)->Unit,develop:(String)->Unit,addTask:()->Unit) {
    val records = state.records
    val tomorrow = state.tomorrow
    val completed = state.completed
    val idea = state.idea
    val review = state.review
    val profile = state.profile
    val reads = state.reads
    val date = state.date
    val locale=LocalConfiguration.current.locales[0]
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("today-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Today",nav,date.format(DateTimeFormatter.ofPattern("MMMM d · EEEE",locale)),status)}
        if(reads["profile"]=="ready" && profile.notes==0) item {
            PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Column(Modifier.padding(16.dp)) {
                Text("A place for every thought",style=MaterialTheme.typography.titleMedium)
                TextButton({actions.cloud()}) {Text("How cloud AI works")}
            }}
        }
        tomorrowSection(tomorrow, date) { actions.selectDate(date.plusDays(1)); nav.navigate(PlannerRoutes.Calendar) }
        scheduleSection(records, date, reads, actions.retry, edit)
        anytimeSection(records, date, reads, actions.retry, expanded, { expanded = it }, actions.complete, edit, addTask)
        ideaOfDaySection(idea, reads, actions.retry, edit, develop, actions.later)
        doneTodaySection(completed, showDone, { showDone = it }, actions.complete, edit)
        if(review.isNotEmpty()) item {
            Spacer(Modifier.height(20.dp))
            PlannerCard(onClick={nav.navigate(PlannerRoutes.Review)}) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {PlannerIcon(Glyph.Leaf,Modifier.size(24.dp));Column(Modifier.padding(horizontal=12.dp).weight(1f)) {Text("Worth revisiting",style=MaterialTheme.typography.titleMedium);Text(review.first().title,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};PlannerIcon(Glyph.Right,Modifier.size(18.dp))}}
        }
    }
}
