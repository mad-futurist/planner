package app.planner.features

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.core.designsystem.*
import app.planner.core.model.*
import app.planner.domain.isScheduledOnDay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val ScreenPadding=PaddingValues(top=10.dp,bottom=24.dp)

@Composable internal fun JournalScreen(vm:PlannerViewModel,nav:NavHostController,edit:(String)->Unit,review:(String)->Unit) {
    val notes by vm.journal.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("journal-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Journal",nav,"Original thoughts, always here.")}
        if(notes.isEmpty()) item {LocalQueryHint(vm,"journal","No notes on this page. Write anything below.")}
        items(notes,key={it.id}) {note->
            val records by remember(note.id) {vm.repository.dao.observeRecordsForNote(note.id)}.collectAsStateWithLifecycle(emptyList())
            PlannerCard {Column(Modifier.padding(16.dp)) {
                Text(note.originalContent,style=MaterialTheme.typography.bodyLarge)
                if(note.content!=note.originalContent) Text("Updated: ${note.content}",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
                Text(Instant.parse(note.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")),Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(note.privacyMode=="LOCAL_ONLY") "Private · on this device" else when(note.processingStatus) {"organized"->"Organized";"review"->"Needs review";"unconfigured"->"AI is not configured";"failed"->"AI processing failed · note saved";else->"Saved · interpretation pending"},style=MaterialTheme.typography.bodySmall)
                for(record in records) TextButton({edit(record.id)}) {Text("${record.baseKind.replaceFirstChar {it.uppercase()}} · ${record.title}${if(record.state=="archived") " · Archived" else ""}",maxLines=2)}
                Row {TextButton({review(note.id)}) {Text(if(records.isEmpty()) "Organize / review" else "Add interpretation")};if(note.privacyMode!="LOCAL_ONLY") TextButton({vm.action {vm.repository.reprocess(note)}}) {Text("Retry AI")}}
            }}
        }
        item {Row {TextButton({vm.page.value=(page-1).coerceAtLeast(0)},enabled=page>0) {Text("Newer")};TextButton({vm.page.value=page+1},enabled=notes.size==50) {Text("Older")}}}
    }
}

@Composable internal fun TriageScreen(vm:PlannerViewModel,nav:NavHostController,edit:(String)->Unit,review:(String)->Unit) {
    val notes by vm.triage.collectAsStateWithLifecycle()
    val tasks by vm.taskReview.collectAsStateWithLifecycle()
    val date by vm.today.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    var split by remember {mutableStateOf<Record?>(null)}
    var actionTitle by remember {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("review-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Review",nav,"A few things, at your pace.")}
        item {SectionHeader("Unfinished thoughts",Glyph.Leaf)}
        if(tasks.isEmpty()) item {LocalQueryHint(vm,"taskReview","Nothing older to revisit today.")}
        items(tasks,key={"task:${it.id}"}) {task->PlannerCard {Column(Modifier.padding(16.dp)) {Text(task.title,style=MaterialTheme.typography.titleMedium);Text(taskDateLabel(task),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row {TextButton({vm.action {vm.repository.edit(task.copy(plannedFor=date.toString()))}}) {Text("Today")};TextButton({vm.action {vm.repository.snoozeTask(task,date)}}) {Text("Later")};TextButton({edit(task.id)}) {Text("Details")}};Row {TextButton({split=task;actionTitle=""}) {Text("Smaller action")};TextButton({vm.action {vm.repository.edit(task.copy(state="archived"))}}) {Text("Let go")}}}}}
        item {SectionHeader("Needs your meaning",Glyph.Sparkles,violet=true)}
        val visible=notes.filter {metadata["triage:${it.id}"]!=date.toString()}
        if(visible.isEmpty()) item {LocalQueryHint(vm,"triage","No interpretations need a decision right now.")}
        items(visible,key={it.id}) {note->PlannerCard {Column(Modifier.padding(16.dp)) {Text(note.originalContent);Row {TextButton({review(note.id)}) {Text("Review")};TextButton({vm.action {vm.repository.dao.meta(Meta("triage:${note.id}",date.toString()))}}) {Text("Later")}}}}}
    }
    split?.let {origin->AlertDialog(onDismissRequest={split=null},title={Text("One smaller action")},text={Column {Text(origin.title);OutlinedTextField(actionTitle,{actionTitle=it},label={Text("Next action")},modifier=Modifier.fillMaxWidth());Text("The original task stays saved. This action will be planned for today. New text follows your capture privacy choice; private sources stay private.",style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton({vm.action {vm.repository.linkedTask(origin,actionTitle,date.toString(),private=vm.privateCapture.value);vm.repository.snoozeTask(origin,date);split=null;vm.message.value="Smaller action saved"}},enabled=actionTitle.isNotBlank()) {Text("Create action")}},dismissButton={TextButton({split=null}) {Text("Cancel")}})}
}
