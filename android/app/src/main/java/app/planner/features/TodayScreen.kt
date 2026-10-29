package app.planner.features

import app.planner.navigation.PlannerRoutes

import app.planner.navigation.ScreenNavigation

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.planner.core.designsystem.*
import app.planner.domain.model.*
import app.planner.domain.isScheduledOnDay
import java.time.*
import java.time.format.DateTimeFormatter

data class TodayActions(val complete: (Record) -> Unit, val later: (Record) -> Unit, val selectDate: (LocalDate) -> Unit, val cloud: () -> Unit, val retry: () -> Unit)

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
    val scheduled=records.filter {isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedBy {it.startAt}
    val anytime=records.filter {it.baseKind=="task" && !isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedWith(compareByDescending<Record> {it.pinned}.thenBy {it.createdAt})
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("today-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Today",nav,date.format(DateTimeFormatter.ofPattern("MMMM d · EEEE",locale)),status)}
        if(reads["profile"]=="ready" && profile.notes==0) item {
            PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Column(Modifier.padding(16.dp)) {
                Text("A place for every thought",style=MaterialTheme.typography.titleMedium)
                TextButton({actions.cloud()}) {Text("How cloud AI works")}
            }}
        }
        val next=tomorrow.filter {isScheduledOnDay(it,date.plusDays(1),ZoneId.systemDefault())}.minByOrNull {it.startAt!!}
        if(next!=null) item {
            PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer,endColor=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.65f),onClick={actions.selectDate(date.plusDays(1));nav.navigate(PlannerRoutes.Calendar)}) {
                Box(Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                    Landscape(Modifier.align(Alignment.BottomStart).width(86.dp).height(58.dp),sunrise=true)
                    Row(Modifier.padding(start=91.dp,end=6.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text("Tomorrow · ${next.title} ${recordClock(next.startAt)}",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.secondary,maxLines=2,overflow=TextOverflow.Ellipsis);if(tomorrow.size>1) Text("+${tomorrow.size-1} more",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
                        CircleAction(Glyph.Right,"Open tomorrow",{actions.selectDate(date.plusDays(1));nav.navigate(PlannerRoutes.Calendar)})
                    }
                }
            }
        }
        item {SectionHeader("Schedule",Glyph.Calendar)}
        if(scheduled.isEmpty()) item {LocalQueryHint(state.reads,"today","No timed commitments today.",actions.retry)}
        items(scheduled,key={"schedule:${it.id}"}) {ScheduleCard(it,date) {edit(it.id)}}
        item {SectionHeader("Anytime",Glyph.Sun,warm=true)}
        if(anytime.isEmpty()) item {LocalQueryHint(state.reads,"today","Choose a small focus for today.",actions.retry);TextButton(addTask,Modifier.padding(start=42.dp)) {Text("+ Add a task")}}
        items(if(expanded) anytime else anytime.take(2),key={"task:${it.id}"}) {Box(Modifier.padding(start=44.dp)) {TaskRow(it,actions.complete,compact=true) {edit(it.id)}}}
        if(anytime.size>2) item {Box(Modifier.padding(start=44.dp)) {ExpandRow(if(expanded) "Show less" else "+${anytime.size-2} more",Modifier.testTag("anytime-more"),expanded) {expanded=!expanded}}}
        item {SectionHeader("Idea of the day",Glyph.Bulb,violet=true)}
        val savedIdea=idea
        if(savedIdea==null) item {LocalQueryHint(state.reads,"idea","Space for a thought to revisit.",actions.retry)} else item {
            Box(Modifier.padding(start=44.dp)) {IdeaCard(savedIdea,{edit(savedIdea.id)},{develop(savedIdea.id)},{actions.later(savedIdea)})}
        }
        item {
            Spacer(Modifier.height(18.dp))
            PlannerCard(color=MaterialTheme.colorScheme.primaryContainer,onClick={showDone=!showDone}) {
                Box(Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Landscape(Modifier.align(Alignment.BottomEnd).width(150.dp).height(44.dp))
                    Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary,CircleShape),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.Check,Modifier.size(20.dp),MaterialTheme.colorScheme.onPrimary)}
                        Text("Done today: ${completed.size}",Modifier.padding(start=12.dp),style=MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if(showDone) items(completed,key={"done:${it.id}"}) {TaskRow(it,actions.complete) {edit(it.id)}}
        if(review.isNotEmpty()) item {
            Spacer(Modifier.height(20.dp))
            PlannerCard(onClick={nav.navigate(PlannerRoutes.Review)}) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {PlannerIcon(Glyph.Leaf,Modifier.size(24.dp));Column(Modifier.padding(horizontal=12.dp).weight(1f)) {Text("Worth revisiting",style=MaterialTheme.typography.titleMedium);Text(review.first().title,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};PlannerIcon(Glyph.Right,Modifier.size(18.dp))}}
        }
    }
}
