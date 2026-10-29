package app.planner.presentation.tasks

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
import androidx.compose.ui.unit.*
import app.planner.presentation.common.LocalQueryHint
import app.planner.presentation.common.ScreenHeader
import app.planner.presentation.common.ScreenPadding
import app.planner.presentation.common.TaskRow
import app.planner.presentation.design.components.ExpandRow
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.SectionHeader
import app.planner.presentation.navigation.ScreenNavigation
import app.planner.presentation.tasks.components.TaskFilterLabel
import java.time.*

@Composable internal fun TasksScreen(state:TasksUiState,actions:TasksActions,nav:ScreenNavigation,status:String,edit:(String)->Unit,addTask:()->Unit) {
    val tasks = state.tasks
    val filter = state.filter
    val counts = state.counts
    val page = state.page
    var views by remember {mutableStateOf(false)}
    var pinnedOpen by rememberSaveable {mutableStateOf(true)}
    var sectionOpen by rememberSaveable {mutableStateOf(true)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("tasks-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Tasks",nav,"Stay on track, one step at a time.",status)}
        item {
            Surface(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.3f),shape=CircleShape) {
                Row(Modifier.fillMaxWidth().padding(5.dp)) {for((key,label) in listOf("today" to "Today","upcoming" to "Upcoming","someday" to "Someday")) Surface(onClick={actions.filterTasks(key)},color=if(filter==key) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,shape=CircleShape,modifier=Modifier.weight(1f).testTag("filter-$key")) {
                    TaskFilterLabel(label,counts[key]?:0,filter==key)
                }}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {Box {TextButton({views=true},Modifier.testTag("task-views")) {Text(if(filter in setOf("all","completed","archived")) "${filter.replaceFirstChar {it.uppercase()}} ▾" else "More views ▾",style=MaterialTheme.typography.labelMedium)};DropdownMenu(views,{views=false}) {for((key,label) in listOf("all" to "All unfinished","completed" to "Completed","archived" to "Archived")) DropdownMenuItem({Text(label)},{actions.filterTasks(key);views=false})}}}
        }
        val pinned=tasks.filter {it.pinned}
        if(pinned.isNotEmpty()) {
            item {SectionHeader("Pinned",Glyph.Pin,pinned.size,warm=true,expanded=pinnedOpen,action={pinnedOpen=!pinnedOpen})}
            if(pinnedOpen) items(pinned,key={it.id}) {Box(Modifier.padding(start=28.dp)) {TaskRow(it,actions.complete) {edit(it.id)}}}
        }
        item {SectionHeader(when(filter) {"all"->"All unfinished";"today"->"Today & earlier";else->filter.replaceFirstChar {it.uppercase()}},when(filter) {"someday"->Glyph.Leaf;"upcoming"->Glyph.Calendar;else->Glyph.Sun},expanded=sectionOpen,action={sectionOpen=!sectionOpen})}
        if(tasks.isEmpty()) item {LocalQueryHint(state.reads,"tasks",when(filter) {"someday"->"Tasks without a date live here. Nothing gets lost.";"archived"->"Items you let go of remain restorable here.";"completed"->"Completed tasks will appear here.";else->"A little breathing room. Add a task when you need one."},actions.retry)}
        if(sectionOpen) items(tasks.filterNot {it.pinned},key={it.id}) {Box(Modifier.padding(start=28.dp)) {TaskRow(it,actions.complete) {edit(it.id)}}}
        item {Box(Modifier.padding(start=28.dp,top=4.dp)) {ExpandRow("+ Add task",onClick=addTask)}}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({actions.page((page-1).coerceAtLeast(0))},enabled=page>0) {Text("Previous")};TextButton({actions.page(page+1)},enabled=tasks.size==50) {Text("More tasks")}};Text("No date is required. Earlier tasks stay here until you decide.",Modifier.padding(start=28.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
