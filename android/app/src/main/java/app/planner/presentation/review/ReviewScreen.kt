package app.planner.presentation.review

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.planner.presentation.common.LocalQueryHint
import app.planner.presentation.common.ScreenHeader
import app.planner.presentation.common.ScreenPadding
import app.planner.presentation.design.components.Glyph
import app.planner.presentation.design.components.PlannerCard
import app.planner.presentation.design.components.SectionHeader
import app.planner.presentation.design.format.taskDateLabel
import app.planner.presentation.navigation.ScreenNavigation
import java.time.*

@Composable internal fun ReviewScreen(state:ReviewUiState,actions:ReviewActions,nav:ScreenNavigation,edit:(String)->Unit,review:(String)->Unit) {
    val notes = state.notes
    val tasks = state.tasks
    val date = state.date
    val metadata = state.metadata
    val split = state.split
    var actionTitle by remember {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("review-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Review",nav,"A few things, at your pace.")}
        item {SectionHeader("Unfinished thoughts",Glyph.Leaf)}
        if(tasks.isEmpty()) item {LocalQueryHint(state.reads,"taskReview","Nothing older to revisit today.",actions.retry)}
        items(tasks,key={"task:${it.id}"}) {task->PlannerCard {Column(Modifier.padding(16.dp)) {Text(task.title,style=MaterialTheme.typography.titleMedium);Text(taskDateLabel(task),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row {TextButton({actions.plan(task)}) {Text("Today")};TextButton({actions.snooze(task)}) {Text("Later")};TextButton({edit(task.id)}) {Text("Details")}};Row {TextButton({actions.split(task);actionTitle=""}) {Text("Smaller action")};TextButton({actions.archive(task)}) {Text("Let go")}}}}}
        item {SectionHeader("Needs your meaning",Glyph.Sparkles,violet=true)}
        val visible=notes.filter {metadata["triage:${it.id}"]!=date.toString()}
        if(visible.isEmpty()) item {LocalQueryHint(state.reads,"triage","No interpretations need a decision right now.",actions.retry)}
        items(visible,key={it.id}) {note->PlannerCard {Column(Modifier.padding(16.dp)) {Text(note.originalContent);Row {TextButton({review(note.id)}) {Text("Review")};TextButton({actions.postpone(note.id)}) {Text("Later")}}}}}
    }
    split?.let {origin->AlertDialog(onDismissRequest={actions.split(null)},title={Text("One smaller action")},text={Column {Text(origin.title);OutlinedTextField(actionTitle,{actionTitle=it},label={Text("Next action")},modifier=Modifier.fillMaxWidth());Text("The original task stays saved. This action will be planned for today. New text follows your capture privacy choice; private sources stay private.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton({actions.createAction(actionTitle)},enabled=actionTitle.isNotBlank() && !state.saving) {Text("Create action")}},dismissButton={TextButton({actions.split(null)}) {Text("Cancel")}})}
}
