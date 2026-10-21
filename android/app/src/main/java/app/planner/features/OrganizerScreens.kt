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
import com.google.gson.JsonParser
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val ScreenPadding=PaddingValues(top=10.dp,bottom=24.dp)

@Composable internal fun TodayScreen(vm:PlannerViewModel,nav:NavHostController,status:String,edit:(String)->Unit,develop:(String)->Unit,addTask:()->Unit) {
    val records by vm.todayItems.collectAsStateWithLifecycle()
    val tomorrow by vm.tomorrowItems.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val idea by vm.idea.collectAsStateWithLifecycle()
    val review by vm.taskReview.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reads by vm.localReads.collectAsStateWithLifecycle()
    val date by vm.today.collectAsStateWithLifecycle()
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
                Text("New notes stay private in Journal. Use + to create a task yourself, or allow cloud AI to interpret future notes.",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodyMedium)
                TextButton({vm.cloudExplanation.value=true}) {Text("How cloud AI works")}
            }}
        }
        val next=tomorrow.filter {isScheduledOnDay(it,date.plusDays(1),ZoneId.systemDefault())}.minByOrNull {it.startAt!!}
        if(next!=null) item {
            PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer,endColor=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.65f),onClick={vm.selectedDate.value=date.plusDays(1);nav.navigate("calendar")}) {
                Box(Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                    Landscape(Modifier.align(Alignment.BottomStart).width(86.dp).height(58.dp),sunrise=true)
                    Row(Modifier.padding(start=91.dp,end=6.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {Text("Tomorrow · ${next.title} ${recordClock(next.startAt)}",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.secondary,maxLines=2,overflow=TextOverflow.Ellipsis);if(tomorrow.size>1) Text("+${tomorrow.size-1} more",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}
                        CircleAction(Glyph.Right,"Open tomorrow",{vm.selectedDate.value=date.plusDays(1);nav.navigate("calendar")})
                    }
                }
            }
        }
        item {SectionHeader("Schedule",Glyph.Calendar)}
        if(scheduled.isEmpty()) item {LocalQueryHint(vm,"today","No timed commitments today.")}
        items(scheduled,key={"schedule:${it.id}"}) {ScheduleCard(it,date) {edit(it.id)}}
        item {SectionHeader("Anytime",Glyph.Sun,warm=true)}
        if(anytime.isEmpty()) item {LocalQueryHint(vm,"today","Choose a small focus for today.");TextButton(addTask,Modifier.padding(start=42.dp)) {Text("+ Add a task")}}
        items(if(expanded) anytime else anytime.take(2),key={"task:${it.id}"}) {Box(Modifier.padding(start=44.dp)) {TaskRow(vm,it,compact=true) {edit(it.id)}}}
        if(anytime.size>2) item {Box(Modifier.padding(start=44.dp)) {ExpandRow(if(expanded) "Show less" else "+${anytime.size-2} more",Modifier.testTag("anytime-more"),expanded) {expanded=!expanded}}}
        item {SectionHeader("Idea of the day",Glyph.Bulb,violet=true)}
        val savedIdea=idea
        if(savedIdea==null) item {LocalQueryHint(vm,"idea","Space for a thought to revisit.")} else item {
            Box(Modifier.padding(start=44.dp)) {IdeaCard(savedIdea,{edit(savedIdea.id)},{develop(savedIdea.id)},{vm.action {vm.repository.later(savedIdea,date)}})}
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
        if(showDone) items(completed,key={"done:${it.id}"}) {TaskRow(vm,it) {edit(it.id)}}
        if(review.isNotEmpty()) item {
            Spacer(Modifier.height(20.dp))
            PlannerCard(onClick={nav.navigate("triage")}) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {PlannerIcon(Glyph.Leaf,Modifier.size(24.dp));Column(Modifier.padding(horizontal=12.dp).weight(1f)) {Text("Worth revisiting",style=MaterialTheme.typography.titleMedium);Text(review.first().title,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};PlannerIcon(Glyph.Right,Modifier.size(18.dp))}}
        }
    }
}

@Composable internal fun CalendarScreen(vm:PlannerViewModel,nav:NavHostController,status:String,edit:(String)->Unit) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val records by vm.calendar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val firstDay=if(settings["weekStart"]=="monday") DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val week=date.with(TemporalAdjusters.previousOrSame(firstDay))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("calendar-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Calendar",nav,status=status)}
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {TextButton({DatePickerDialog(context,{_,y,m,d->vm.selectedDate.value=LocalDate.of(y,m+1,d)},date.year,date.monthValue-1,date.dayOfMonth).show()},contentPadding=PaddingValues(2.dp)) {Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(8.dp));PlannerIcon(Glyph.Down,Modifier.size(15.dp))}}
                CircleAction(Glyph.Left,"Previous week",{vm.selectedDate.value=date.minusWeeks(1)})
                CircleAction(Glyph.Right,"Next week",{vm.selectedDate.value=date.plusWeeks(1)})
            }
        }
        item {
            PlannerCard(color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.30f)) {
                Row(Modifier.fillMaxWidth().padding(5.dp)) {
                    repeat(7) {offset->val day=week.plusDays(offset.toLong());val chosen=day==date
                        val total by remember(day) {val bounds=app.planner.domain.dayBounds(day,ZoneId.systemDefault());vm.repository.dao.dayCount(bounds.day,bounds.start,bounds.end)}.collectAsStateWithLifecycle(0)
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(if(chosen) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent).clickable {vm.selectedDate.value=day}.heightIn(min=76.dp).padding(vertical=10.dp).semantics {contentDescription="Select $day"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(day.format(DateTimeFormatter.ofPattern("EEE")).uppercase(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${day.dayOfMonth}",style=MaterialTheme.typography.titleLarge,fontWeight=if(chosen) FontWeight.Bold else FontWeight.Normal)
                            if(total>0) Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary,CircleShape)) else Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
        item {Row(Modifier.padding(top=18.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton({vm.selectedDate.value=LocalDate.now()}) {Text("Today")}};SectionHeader("Scheduled events",Glyph.Sun)}
        val scheduled=records.filter {isScheduledOnDay(it,date,ZoneId.systemDefault())}.sortedBy {it.startAt}
        if(scheduled.isEmpty()) item {LocalQueryHint(vm,"calendar","No timed commitments on this day.")}
        items(scheduled,key={"scheduled:${it.id}"}) {ScheduleCard(it,date) {edit(it.id)}}
        item {SectionHeader("Planned / due",Glyph.Calendar,warm=true)}
        val tasks=records.filter {it.baseKind=="task" && !isScheduledOnDay(it,date,ZoneId.systemDefault())}
        if(tasks.isEmpty()) item {LocalQueryHint(vm,"calendar","Nothing planned or due on this day.")}
        items(tasks,key={"due:${it.id}"}) {Box(Modifier.padding(start=44.dp)) {TaskRow(vm,it) {edit(it.id)}}}
        item {Text("Deadlines stay separate from appointments.",Modifier.padding(start=44.dp,top=14.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable internal fun TasksScreen(vm:PlannerViewModel,nav:NavHostController,status:String,edit:(String)->Unit,addTask:()->Unit) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val filter by vm.taskFilter.collectAsStateWithLifecycle()
    val counts by vm.taskCounts.collectAsStateWithLifecycle()
    val page by vm.taskPage.collectAsStateWithLifecycle()
    var views by remember {mutableStateOf(false)}
    var pinnedOpen by rememberSaveable {mutableStateOf(true)}
    var sectionOpen by rememberSaveable {mutableStateOf(true)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("tasks-list"),contentPadding=ScreenPadding) {
        item {ScreenHeader("Tasks",nav,"Stay on track, one step at a time.",status)}
        item {
            Surface(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.3f),shape=CircleShape) {
                Row(Modifier.fillMaxWidth().padding(5.dp)) {for((key,label) in listOf("today" to "Today","upcoming" to "Upcoming","someday" to "Someday")) Surface(onClick={vm.filterTasks(key)},color=if(filter==key) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,shape=CircleShape,modifier=Modifier.weight(1f).testTag("filter-$key")) {
                    TaskFilterLabel(label,counts[key]?:0,filter==key)
                }}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {Box {TextButton({views=true},Modifier.testTag("task-views")) {Text(if(filter in setOf("all","completed","archived")) "${filter.replaceFirstChar {it.uppercase()}} ▾" else "More views ▾",style=MaterialTheme.typography.labelMedium)};DropdownMenu(views,{views=false}) {for((key,label) in listOf("all" to "All unfinished","completed" to "Completed","archived" to "Archived")) DropdownMenuItem({Text(label)},{vm.filterTasks(key);views=false})}}}
        }
        val pinned=tasks.filter {it.pinned}
        if(pinned.isNotEmpty()) {
            item {SectionHeader("Pinned",Glyph.Pin,pinned.size,warm=true,expanded=pinnedOpen,action={pinnedOpen=!pinnedOpen})}
            if(pinnedOpen) items(pinned,key={it.id}) {Box(Modifier.padding(start=28.dp)) {TaskRow(vm,it) {edit(it.id)}}}
        }
        item {SectionHeader(when(filter) {"all"->"All unfinished";"today"->"Today & earlier";else->filter.replaceFirstChar {it.uppercase()}},when(filter) {"someday"->Glyph.Leaf;"upcoming"->Glyph.Calendar;else->Glyph.Sun},expanded=sectionOpen,action={sectionOpen=!sectionOpen})}
        if(tasks.isEmpty()) item {LocalQueryHint(vm,"tasks",when(filter) {"someday"->"Tasks without a date live here. Nothing gets lost.";"archived"->"Items you let go of remain restorable here.";"completed"->"Completed tasks will appear here.";else->"A little breathing room. Add a task when you need one."})}
        if(sectionOpen) items(tasks.filterNot {it.pinned},key={it.id}) {Box(Modifier.padding(start=28.dp)) {TaskRow(vm,it) {edit(it.id)}}}
        item {Box(Modifier.padding(start=28.dp,top=4.dp)) {ExpandRow("+ Add task",onClick=addTask)}}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({vm.taskPage.value=(page-1).coerceAtLeast(0)},enabled=page>0) {Text("Previous")};TextButton({vm.taskPage.value=page+1},enabled=tasks.size==50) {Text("More tasks")}};Text("No date is required. Earlier tasks stay here until you decide.",Modifier.padding(start=28.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable private fun TaskFilterLabel(label:String,count:Int,selected:Boolean) {
    val badge: @Composable () -> Unit = {Surface(shape=CircleShape,color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer) {Text("$count",Modifier.padding(horizontal=6.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=if(selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)}}
    if(LocalDensity.current.fontScale>1.2f) Column(Modifier.heightIn(min=48.dp).padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1);badge()}
    else Row(Modifier.heightIn(min=48.dp).padding(horizontal=5.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterHorizontally)) {Text(label,style=MaterialTheme.typography.labelMedium);badge()}
}

@Composable internal fun IdeasScreen(vm:PlannerViewModel,nav:NavHostController,status:String,edit:(String)->Unit,develop:(String)->Unit) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val query by vm.ideaSearch.collectAsStateWithLifecycle()
    val page by vm.ideaPage.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("ideas-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Ideas",nav,"A little room for what could be.",status)}
        item {OutlinedTextField(query,{vm.ideaPage.value=0;vm.ideaSearch.value=it},Modifier.fillMaxWidth().testTag("idea-search"),placeholder={Text("Find a saved idea…")},singleLine=true,shape=RoundedCornerShape(18.dp))}
        item {PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Box(Modifier.fillMaxWidth().heightIn(min=72.dp)) {Landscape(Modifier.align(Alignment.BottomEnd).width(130.dp).height(70.dp));Column(Modifier.padding(16.dp).fillMaxWidth(.65f)) {Text("Keep the possibility",style=MaterialTheme.typography.titleMedium);Text("No deadline. Come back when it feels right.",style=MaterialTheme.typography.bodySmall)}}}}
        if(ideas.isEmpty()) item {LocalQueryHint(vm,"ideas","Capture an idea below, or organize an existing note from Journal.")}
        items(ideas,key={it.id}) {idea->IdeaCard(idea,{edit(idea.id)},{develop(idea.id)},convert={vm.action {vm.repository.linkedTask(idea,private=vm.privateCapture.value);vm.message.value="Task created · your idea is still saved"}})}
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {TextButton({vm.ideaPage.value=(page-1).coerceAtLeast(0)},enabled=page>0) {Text("Previous")};TextButton({vm.ideaPage.value=page+1},enabled=ideas.size==50) {Text("More ideas")}}}
    }
}

@Composable internal fun ProfileScreen(vm:PlannerViewModel,nav:NavHostController) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("profile-list"),contentPadding=ScreenPadding,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {ScreenHeader("Profile",nav,"Your thoughts, in one place.")}
        item {PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerLow,CircleShape),contentAlignment=Alignment.Center) {PlannerIcon(Glyph.User,Modifier.size(27.dp))};Column(Modifier.padding(start=14.dp)) {Text(if(metadata["owner"]==null) "Local device profile" else "Connected workspace",style=MaterialTheme.typography.titleMedium);Text(metadata["owner"]?.let {"Workspace ${it.take(8)}"} ?: "Private by default · no account needed",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Metric("Tasks completed",profile.completed,Modifier.weight(1f),false);Metric("Ideas saved",profile.ideas,Modifier.weight(1f),true)}}
        item {Text(if(profile.notes==0) "Your space starts with a thought. Write anything below." else "${profile.notes} original notes · ${profile.tasks} unfinished tasks",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(collections.isNotEmpty()) item {SectionHeader("Your collections",Glyph.Tag);for(collection in collections) Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {Text(collection.name,Modifier.weight(1f));Text("${collection.count}")}}
        item {SectionHeader("Your space",Glyph.Leaf)}
        item {ProfileLink("Journal","Original notes, always preserved",Glyph.Book) {nav.navigate("journal")}}
        item {ProfileLink("Review","A few decisions, at your pace",Glyph.Sun) {nav.navigate("triage")}}
        item {ProfileLink("Settings","Privacy, capture and connection",Glyph.Palette) {nav.navigate("settings")}}
        item {Landscape(Modifier.fillMaxWidth().height(85.dp))}
    }
}
@Composable private fun Metric(label:String,count:Int,modifier:Modifier,violet:Boolean) {PlannerCard(modifier,color=if(violet) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {Column(Modifier.padding(16.dp)) {Text("$count",style=MaterialTheme.typography.headlineLarge);Text(label,style=MaterialTheme.typography.bodySmall)}}}
@Composable internal fun ProfileLink(title:String,subtitle:String,glyph:Glyph,click:()->Unit) {PlannerCard(onClick=click) {Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {PlannerIcon(glyph,Modifier.size(23.dp));Column(Modifier.weight(1f).padding(horizontal=14.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};PlannerIcon(Glyph.Right,Modifier.size(17.dp))}}}

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
