package app.planner.features

import app.planner.navigation.ScreenNavigation

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.planner.core.designsystem.*
import app.planner.domain.model.*
import app.planner.domain.isScheduledOnDay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class CalendarActions(val complete: (Record) -> Unit, val selectDate: (LocalDate) -> Unit, val retry: () -> Unit)

@Composable internal fun CalendarScreen(state:CalendarUiState,actions:CalendarActions,nav:ScreenNavigation,status:String,edit:(String)->Unit) {
    val date = state.date
    val records = state.records
    val settings = state.settings
    val context=LocalContext.current
    val firstDay=if(settings["weekStart"]=="monday") DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val week=date.with(TemporalAdjusters.previousOrSame(firstDay))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("calendar-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Calendar",nav,status=status)}
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {TextButton({DatePickerDialog(context,{_,y,m,d->actions.selectDate(LocalDate.of(y,m+1,d))},date.year,date.monthValue-1,date.dayOfMonth).show()},contentPadding=PaddingValues(2.dp)) {Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(8.dp));PlannerIcon(Glyph.Down,Modifier.size(15.dp))}}
                CircleAction(Glyph.Left,"Previous week",{actions.selectDate(date.minusWeeks(1))})
                CircleAction(Glyph.Right,"Next week",{actions.selectDate(date.plusWeeks(1))})
            }
        }
        item {
            PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.30f)) {
                Row(Modifier.fillMaxWidth().padding(5.dp)) {
                    repeat(7) {offset->val day=week.plusDays(offset.toLong());val chosen=day==date
                        val total = state.counts[day] ?: 0
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(if(chosen) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent).clickable {actions.selectDate(day)}.heightIn(min=76.dp).padding(vertical=10.dp).semantics {contentDescription="Select $day"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(day.format(DateTimeFormatter.ofPattern("EEE")).uppercase(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${day.dayOfMonth}",style=MaterialTheme.typography.titleLarge,fontWeight=if(chosen) FontWeight.Bold else FontWeight.Normal)
                            if(total>0) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary,CircleShape)) else Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
        item {Row(Modifier.padding(top=18.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton({actions.selectDate(LocalDate.now())}) {Text("Today")}};SectionHeader("Scheduled events",Glyph.Sun)}
        val scheduled=records.filter {isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedBy {it.startAt}
        if(scheduled.isEmpty()) item {LocalQueryHint(state.reads,"calendar","No timed commitments on this day.",actions.retry)}
        items(scheduled,key={"scheduled:${it.id}"}) {ScheduleCard(it,date) {edit(it.id)}}
        item {SectionHeader("Planned / due",Glyph.Calendar,warm=true)}
        val tasks=records.filter {it.baseKind=="task" && !isScheduledOnDay(it,date,ZoneId.systemDefault())}
        if(tasks.isEmpty()) item {LocalQueryHint(state.reads,"calendar","Nothing planned or due on this day.",actions.retry)}
        items(tasks,key={"due:${it.id}"}) {Box(Modifier.padding(start=44.dp)) {TaskRow(it,actions.complete) {edit(it.id)}}}
    }
}
