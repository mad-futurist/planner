package app.planner.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.planner.core.database.*
import app.planner.core.model.*
import app.planner.core.designsystem.PlannerTheme
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlannerUI(vm: PlannerViewModel) {
    val nav = rememberNavController()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val private by vm.privateCapture.collectAsStateWithLifecycle()
    val kind by vm.captureKind.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addMenu by remember { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingId by rememberSaveable { mutableStateOf<String?>(null) }
    var developingId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val focusCapture by vm.focusCapture.collectAsStateWithLifecycle()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(focusCapture) { if (focusCapture > 0) { focus.requestFocus(); keyboard?.show() } }
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    val localReads by vm.localReads.collectAsStateWithLifecycle()
    val status = when {
        localReads["metadata"] == "error" || localReads["processing"] == "error" -> "Couldn't read saved data"
        localReads["metadata"] != "ready" || localReads["processing"] != "ready" -> "Reading saved notes…"
        metadata["network"] == null -> "Saved locally first"
        processing.getOrDefault("review", 0) > 0 -> "Review needed"
        metadata["network"] == "offline" -> "Waiting for connection"
        metadata["network"] == "unauthorized" -> "Reconnect in Settings"
        metadata["network"] == "unconfigured" -> "AI not connected"
        metadata["ai"] == "unconfigured" || processing.getOrDefault("unconfigured", 0) > 0 -> "AI unavailable"
        processing.getOrDefault("failed", 0) > 0 -> "Some notes need attention"
        processing.getOrDefault("pending", 0) > 0 -> "AI organizing"
        else -> "All organized"
    }
    LaunchedEffect(message) {
        message?.let {
            val result = snackbar.showSnackbar(it, actionLabel = if (vm.undoId.value != null) "Undo" else null, withDismissAction = vm.undoId.value != null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undo()
            vm.undoId.value = null; vm.message.value = null
        }
    }
    PlannerTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Surface(tonalElevation = 0.dp, shadowElevation = 6.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton({ addMenu = true }, Modifier.semantics { contentDescription = "Choose capture type" }) { Text("+", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary) }
                            DropdownMenu(addMenu, { addMenu = false }) {
                                for ((label, value) in listOf("Note · AI organizes" to null, "Task for today" to "task", "Idea" to "idea", "Event" to "event", "Information" to "information")) {
                                    DropdownMenuItem(text = { Text(label) }, onClick = { vm.captureKind.value = value; addMenu = false; focus.requestFocus() })
                                }
                            }
                        }
                        TextField(draft, vm::setDraft, Modifier.weight(1f).focusRequester(focus).testTag("capture"), placeholder = { Text("Write anything…") }, maxLines = 4, colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                        FilledIconButton(onClick = { vm.capture(kind) }, enabled = draft.isNotBlank() && !saving, modifier = Modifier.size(48.dp).testTag("send").semantics { contentDescription = "Save note" }) { Text(if (saving) "…" else "↑", fontSize = 24.sp) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (kind == null) "Saved locally first" else "${kind.orEmpty().replaceFirstChar { it.uppercase() }}${if (kind == "task") " · Today" else ""}", Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton({ vm.setPrivacy(!private) }, Modifier.heightIn(min = 48.dp).testTag("privacy")) { Text(if (private) "Private · device only" else "Cloud AI", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }) { insets ->
            NavHost(nav, "today", Modifier.padding(insets)) {
                composable("today") { TodayScreen(vm, nav, status, { editingId = it }, { developingId = it }) { vm.captureKind.value = "task"; focus.requestFocus() } }
                composable("journal") { JournalScreen(vm, nav, { editingId = it }, { reviewingId = it }) }
                composable("calendar") { CalendarScreen(vm, nav) { editingId = it } }
                composable("ideas") { IdeasScreen(vm, nav, { editingId = it }, { developingId = it }) }
                composable("triage") { TriageScreen(vm, nav) { reviewingId = it } }
                composable("settings") { SettingsScreen(vm, nav) }
            }
        }
        editingId?.let { id ->
            val record by produceState<Record?>(null, id) { value = vm.repository.dao.recordById(id) }
            record?.let { RecordEditor(vm, it) { editingId = null } }
        }
        reviewingId?.let { id ->
            val note by produceState<RawNote?>(null, id) { value = vm.repository.dao.noteById(id) }
            note?.let { InterpretationEditor(vm, it) { reviewingId = null } }
        }
        developingId?.let { id ->
            val record by produceState<Record?>(null, id) { value = vm.repository.dao.recordById(id) }
            record?.let { DevelopmentDialog(vm, it) { developingId = null } }
        }
    }
}

@Composable
fun ScreenHeader(title: String, nav: NavHostController, subtitle: String? = null, status: String? = null) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontFamily = FontFamily.Serif, fontSize = 44.sp, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onSurface)
            Box {
                TextButton({ menu = true }, Modifier.semantics { contentDescription = "Open navigation" }) { Text("•••", fontSize = 20.sp) }
                DropdownMenu(menu, { menu = false }) {
                    for ((label, route) in listOf("Today" to "today", "Journal" to "journal", "Calendar" to "calendar", "Ideas" to "ideas", "Review" to "triage", "Settings" to "settings")) DropdownMenuItem({ Text(label) }, { menu = false; nav.navigate(route) { popUpTo("today"); launchSingleTop = true } })
                }
            }
        }
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        status?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, Modifier.padding(top = 24.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
@Composable private fun QuietEmpty(text: String) { Text(text, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
@Composable private fun LocalQueryHint(vm: PlannerViewModel, key: String, emptyText: String) {
    val reads by vm.localReads.collectAsStateWithLifecycle()
    when (reads[key]) {
        "ready" -> QuietEmpty(emptyText)
        "error" -> { QuietEmpty("Couldn't read saved data. Your input is still here."); TextButton(vm::reloadLocalQueries) { Text("Try again") } }
        else -> QuietEmpty("Reading saved data…")
    }
}
private fun clock(value: String?): String = value?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.orEmpty()
private fun details(record: Record): String = runCatching { JsonParser.parseString(record.fieldsJson).asJsonObject.entrySet().filter { it.value.isJsonPrimitive }.take(2).joinToString(" · ") { it.value.asString } }.getOrDefault("")

@Composable private fun ScheduledRow(record: Record, day: LocalDate, open: () -> Unit) {
    val ongoing = record.startAt?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() < day } == true
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ongoing) "Ongoing" else clock(record.startAt), Modifier.width(60.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Card(onClick = open, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.heightIn(min = 72.dp)) {
                Box(Modifier.width(4.dp).height(72.dp).background(MaterialTheme.colorScheme.primaryContainer))
                Column(Modifier.padding(16.dp)) {
                    Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    val text = details(record)
                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

@Composable private fun TaskRow(vm: PlannerViewModel, record: Record, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (record.pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (record.state == "completed") vm.action { vm.repository.complete(record, true) } else vm.finish(record) }, Modifier.semantics { contentDescription = if (record.state == "completed") "Restore ${record.title}" else "Complete ${record.title}" }) { Text(if (record.state == "completed") "✓" else "○", fontSize = 24.sp) }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) { Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis); if (record.dueDate != null || record.dueAt != null) Text("Due ${record.dueDate ?: record.dueAt?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")) }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (record.pinned) Text("★", Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayScreen(vm: PlannerViewModel, nav: NavHostController, status: String, edit: (String) -> Unit, develop: (String) -> Unit, addTask: () -> Unit) {
    val records by vm.todayItems.collectAsStateWithLifecycle()
    val tomorrow by vm.tomorrowItems.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val idea by vm.idea.collectAsStateWithLifecycle()
    val date by vm.today.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val scheduled = records.filter { app.planner.domain.isScheduledOnDay(it, date, ZoneId.systemDefault()) }
    val anytime = records.filter { it.baseKind == "task" && !app.planner.domain.isScheduledOnDay(it, date, ZoneId.systemDefault()) }.sortedWith(compareByDescending<Record> { it.pinned }.thenBy { it.createdAt })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("today-list"), contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)) {
        item { ScreenHeader("Today", nav, date.format(DateTimeFormatter.ofPattern("MMMM d, EEEE", locale)), status) }
        val next = tomorrow.firstOrNull { app.planner.domain.isScheduledOnDay(it, date.plusDays(1), ZoneId.systemDefault()) }
        if (next != null) item {
            val continues = Instant.parse(next.startAt).atZone(ZoneId.systemDefault()).toLocalDate() < date.plusDays(1)
            Surface(onClick = { vm.selectedDate.value = date.plusDays(1); nav.navigate("calendar") }, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Text("Tomorrow · ${next.title} ${if (continues) "· continues" else clock(next.startAt)}${if (tomorrow.size > 1) " · +${tomorrow.size - 1} more" else ""}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2) }
        }
        item { SectionTitle("Schedule") }
        if (scheduled.isEmpty()) item { LocalQueryHint(vm, "today", "No timed commitments today.") }
        items(scheduled, key = { "schedule:${it.id}" }) { ScheduledRow(it, date) { edit(it.id) } }
        item { SectionTitle("Anytime") }
        if (anytime.isEmpty()) item { LocalQueryHint(vm, "today", "Choose a small focus for today.") }
        items(if (expanded) anytime else anytime.take(2), key = { "task:${it.id}" }) { TaskRow(vm, it) { edit(it.id) } }
        if (anytime.size > 2) item { TextButton({ expanded = !expanded }) { Text(if (expanded) "Show less" else "+${anytime.size - 2} more") } }
        if (anytime.isEmpty()) item { TextButton(addTask) { Text("+ Add a task") } }
        item { SectionTitle("Idea of the day") }
        val savedIdea = idea
        if (savedIdea == null) item { LocalQueryHint(vm, "idea", "Space for a thought to revisit.") } else item {
            Card(onClick = { edit(savedIdea.id) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(savedIdea.title, fontFamily = FontFamily.Serif, fontSize = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (details(savedIdea).isNotEmpty()) Text(details(savedIdea), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ develop(savedIdea.id); vm.action { vm.sync.develop(savedIdea) } }, shape = RoundedCornerShape(16.dp)) { Text("Develop") }
                        TextButton({ vm.action { vm.repository.later(savedIdea, date) } }) { Text("Later") }
                    }
                }
            }
        }
        item { Surface(onClick = { showDone = !showDone }, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) { Text("Done today: ${completed.size}${if (showDone) "  −" else "  +"}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
        if (showDone) items(completed, key = { "done:${it.id}" }) { TaskRow(vm, it) { edit(it.id) } }
    }
}

@Composable private fun JournalScreen(vm: PlannerViewModel, nav: NavHostController, edit: (String) -> Unit, review: (String) -> Unit) {
    val notes by vm.journal.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Journal", nav, "Original thoughts, always here.") }
        if (notes.isEmpty()) item { LocalQueryHint(vm, "journal", "No notes on this page. Write anything below.") }
        items(notes, key = { it.id }) { note ->
            val records by remember(note.id) { vm.repository.dao.observeRecordsForNote(note.id) }.collectAsStateWithLifecycle(emptyList())
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(note.originalContent)
                    if (note.content != note.originalContent) Text("Updated: ${note.content}", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Instant.parse(note.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (note.privacyMode == "LOCAL_ONLY") "Private · on this device" else when (note.processingStatus) { "organized" -> "Organized"; "review" -> "Needs review"; "unconfigured" -> "AI is not configured"; "failed" -> "AI processing failed · note saved"; else -> "Saved · interpretation pending" }, style = MaterialTheme.typography.bodySmall)
                    for (record in records) TextButton({ edit(record.id) }) { Text("${record.baseKind.replaceFirstChar { it.uppercase() }} · ${record.title}${if (record.state == "archived") " · Archived" else ""}", maxLines = 2) }
                    Row { TextButton({ review(note.id) }) { Text(if (records.isEmpty()) "Organize / review" else "Add interpretation") }; if (note.privacyMode != "LOCAL_ONLY") TextButton({ vm.action { vm.repository.reprocess(note) } }) { Text("Retry AI") } }
                }
            }
        }
        item { Row { TextButton({ vm.page.value = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Newer") }; TextButton({ vm.page.value = page + 1 }, enabled = notes.size == 50) { Text("Older") } } }
    }
}

@Composable private fun CalendarScreen(vm: PlannerViewModel, nav: NavHostController, edit: (String) -> Unit) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val records by vm.calendar.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
        item { ScreenHeader("Calendar", nav, "A day at a time.") }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ vm.selectedDate.value = date.minusDays(1) }, Modifier.semantics { contentDescription = "Previous day" }) { Text("‹") }
            TextButton({ DatePickerDialog(context, { _, y, m, d -> vm.selectedDate.value = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.weight(1f)) { Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))) }
            TextButton({ vm.selectedDate.value = date.plusDays(1) }, Modifier.semantics { contentDescription = "Next day" }) { Text("›") }; TextButton({ vm.selectedDate.value = LocalDate.now() }) { Text("Today") }
        } }
        item { SectionTitle("Scheduled") }
        val scheduled = records.filter { app.planner.domain.isScheduledOnDay(it, date, ZoneId.systemDefault()) }
        if (scheduled.isEmpty()) item { LocalQueryHint(vm, "calendar", "No timed commitments.") }
        items(scheduled, key = { "scheduled:${it.id}" }) { ScheduledRow(it, date) { edit(it.id) } }
        item { SectionTitle("Planned / due") }
        val tasks = records.filter { it.baseKind == "task" && !app.planner.domain.isScheduledOnDay(it, date, ZoneId.systemDefault()) }
        if (tasks.isEmpty()) item { LocalQueryHint(vm, "calendar", "Nothing planned or due on this day.") }
        items(tasks, key = { "task:${it.id}" }) { TaskRow(vm, it) { edit(it.id) } }
    }
}

@Composable private fun IdeasScreen(vm: PlannerViewModel, nav: NavHostController, edit: (String) -> Unit, develop: (String) -> Unit) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Ideas", nav, "Thoughts without a deadline.") }
        if (ideas.isEmpty()) item { LocalQueryHint(vm, "ideas", "No ideas yet. Capture one below or organize a Journal note as an idea.") }
        items(ideas, key = { it.id }) { idea ->
            Card(onClick = { edit(idea.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) { Text(idea.title, style = MaterialTheme.typography.titleMedium); Text(details(idea), style = MaterialTheme.typography.bodyMedium); Row { TextButton({ develop(idea.id); vm.action { vm.sync.develop(idea) } }) { Text("Develop") }; TextButton({ vm.action { vm.repository.edit(idea.copy(baseKind = "task", plannedFor = LocalDate.now().toString())) } }) { Text("Make a task") } } }
            }
        }
    }
}

@Composable private fun TriageScreen(vm: PlannerViewModel, nav: NavHostController, review: (String) -> Unit) {
    val notes by vm.triage.collectAsStateWithLifecycle()
    val date by vm.today.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Review", nav, "A few things that need your meaning.") }
        val visible = notes.filter { metadata["triage:${it.id}"] != date.toString() }
        if (visible.isEmpty()) item { LocalQueryHint(vm, "triage", "Nothing needs a decision right now.") }
        items(visible, key = { it.id }) { note -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(note.originalContent); Row { TextButton({ review(note.id) }) { Text("Review") }; TextButton({ vm.action { vm.repository.dao.meta(Meta("triage:${note.id}", date.toString())) } }) { Text("Later") } } } } }
    }
}

@Composable private fun InterpretationEditor(vm: PlannerViewModel, note: RawNote, close: () -> Unit) {
    var title by rememberSaveable(note.id) { mutableStateOf(note.content.take(1000)) }
    var kind by rememberSaveable(note.id) { mutableStateOf("task") }
    var planned by rememberSaveable(note.id) { mutableStateOf<String?>(null) }
    val history by produceState<InterpretationHistory?>(null, note.id) { value = vm.repository.dao.latestHistory(note.id) }
    AlertDialog(onDismissRequest = close, title = { Text("Organize note") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(note.originalContent, style = MaterialTheme.typography.bodySmall)
            history?.let { h ->
                val proposals = runCatching { JsonParser.parseString(h.proposedData).asJsonObject.getAsJsonArray("records") }.getOrNull()
                if (h.validationResult != "valid") Text("The AI proposal needs your review. You choose the kind and dates.", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                proposals?.forEach { p -> TextButton({ val proposal = p.asJsonObject; title = proposal.get("title").asString; kind = proposal.get("baseKind").asString }) { Text("Use title: ${p.asJsonObject.get("title").asString}", maxLines = 2) } }
            }
            KindPicker(kind) { kind = it }
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            if (kind == "task") DateControl("Plan for", planned, { planned = it })
        }
    }, confirmButton = { TextButton({ vm.action { vm.repository.manualInterpretation(note, kind, title, if (kind == "task") planned else null); close() } }, enabled = title.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(close) { Text("Cancel") } })
}

@Composable internal fun KindPicker(kind: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { TextButton({ expanded = true }) { Text("Kind: ${kind.replaceFirstChar { it.uppercase() }}") }; DropdownMenu(expanded, { expanded = false }) { for (value in listOf("task", "event", "idea", "information")) DropdownMenuItem({ Text(value.replaceFirstChar { it.uppercase() }) }, { select(value); expanded = false }) } }
}

@Composable internal fun DateControl(label: String, value: String?, changed: (String?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({ val date = value?.let(LocalDate::parse) ?: LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> changed(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.weight(1f)) { Text("$label: ${value ?: "Not set"}") }
        if (value != null) TextButton({ changed(null) }, Modifier.semantics { contentDescription = "Clear $label" }) { Text("×") }
    }
}

@Composable internal fun TimeControl(label: String, value: String?, zone: String, changed: (String?) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({
            val current = value?.let { Instant.parse(it).atZone(ZoneId.of(zone)) } ?: ZonedDateTime.now(ZoneId.of(zone))
            DatePickerDialog(context, { _, y, m, d ->
                TimePickerDialog(context, { _, hour, minute ->
                    val local = LocalDateTime.of(y, m + 1, d, hour, minute)
                    val timezone = ZoneId.of(zone)
                    val offsets = timezone.rules.getValidOffsets(local)
                    when (offsets.size) {
                        0 -> android.app.AlertDialog.Builder(context).setMessage("This time doesn't exist because the clocks change. Choose another time.").setPositiveButton("OK", null).show()
                        1 -> changed(local.atOffset(offsets[0]).toInstant().toString())
                        else -> android.app.AlertDialog.Builder(context).setTitle("This time occurs twice").setItems(arrayOf("First occurrence", "Second occurrence")) { _, which -> changed(local.atOffset(offsets[which]).toInstant().toString()) }.show()
                    }
                }, current.hour, current.minute, true).show()
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }, Modifier.weight(1f)) { Text("$label: ${value?.let { Instant.parse(it).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("MMM d · HH:mm")) } ?: "Not set"}") }
        if (value != null) TextButton({ changed(null) }) { Text("×") }
    }
}

@Composable private fun DevelopmentDialog(vm: PlannerViewModel, record: Record, close: () -> Unit) {
    val jobMetadata by remember(record.id) { vm.repository.dao.observeMetaKey("job:${record.id}") }.collectAsStateWithLifecycle(null)
    val raw = jobMetadata?.value
    val job = raw?.let { JsonParser.parseString(it).asJsonObject }
    val status = job?.get("status")?.asString ?: "unavailable"
    val steps = if (status == "complete") job?.getAsJsonObject("result")?.getAsJsonArray("steps")?.map { it.asString }.orEmpty() else emptyList()
    var selected by remember(raw) { mutableStateOf(steps.toSet()) }
    var applying by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("Develop this idea") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(record.title)
            when (status) {
                "queued", "running" -> Text("Waiting for AI suggestions. You can keep capturing thoughts.", Modifier.padding(top = 12.dp))
                "complete" -> { Text("Choose what to turn into tasks.", Modifier.padding(vertical = 12.dp)); for (step in steps) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(step in selected, { checked -> selected = if (checked) selected + step else selected - step }); Text(step, Modifier.weight(1f)) } }
                "unconfigured" -> Text("The backend AI provider is not configured. Your idea is saved; add next steps manually or configure the provider and retry.")
                "failed", "stale" -> Text("Suggestions couldn't be used. Your idea is safe. Try again when you're ready.")
                else -> Text("AI isn't available here yet. Check your connection and AI settings; private ideas remain on this device.")
            }
        }
    }, confirmButton = {
        if (steps.isNotEmpty()) TextButton({ applying = true; vm.action { try { vm.sync.approve(record, job!!.get("id").asString, steps.filter { it in selected }); close() } finally { applying = false } } }, enabled = selected.isNotEmpty() && !applying) { Text("Create ${selected.size} tasks") }
        else TextButton({ vm.action { vm.sync.develop(record) } }) { Text("Try again") }
    }, dismissButton = { TextButton(close) { Text("Close") } })
}
