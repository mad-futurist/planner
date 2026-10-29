package app.planner.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.planner.domain.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CaptureActions(
    val setDraft: (String) -> Unit,
    val selectKind: (String?) -> Unit,
    val save: (String?, Boolean) -> Unit,
    val privacy: (Boolean) -> Unit,
    val showClarification: (CaptureDoubt) -> Unit,
    val chooseDay: (PreviewRecord, LocalDate) -> Unit,
    val chooseIntent: (String, String?) -> Unit,
    val resetClarifications: () -> Unit,
    val createEvent: () -> Unit,
    val requestAiPreview: () -> Unit,
    val cancelClarification: () -> Unit,
    val clarify: (CaptureHint) -> Unit,
    val preview: (InterpretationHints) -> CapturePreview,
)

internal class DoubtTransformation(private val doubts: List<CaptureDoubt>, private val hints: List<CaptureHint>, private val uncertain: androidx.compose.ui.graphics.Color, private val confirmed: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(buildAnnotatedString {
        append(text)
        for (doubt in doubts) if (doubt.end <= text.length) addStyle(SpanStyle(color = uncertain, background = uncertain.copy(alpha = .12f), textDecoration = TextDecoration.Underline), doubt.start, doubt.end)
        for (hint in hints) if (hint.end <= text.length) addStyle(SpanStyle(color = confirmed, textDecoration = TextDecoration.Underline), hint.start, hint.end)
    }, OffsetMapping.Identity)
}

/** Observe taps without consuming ordinary cursor/selection gestures. Chips provide an accessible fallback. */
internal fun Modifier.clarificationTaps(layout: () -> TextLayoutResult?, preview: CapturePreview, hints: List<CaptureHint>, open: (CaptureDoubt) -> Unit): Modifier = pointerInput(preview, hints) {
    awaitPointerEventScope {
        var down: androidx.compose.ui.geometry.Offset? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed && !change.previousPressed) down = change.position
            if (!change.pressed && change.previousPressed) {
                val origin = down
                down = null
                if (origin != null && (change.position - origin).getDistance() <= viewConfiguration.touchSlop) {
                    val position = layout()?.getOffsetForPosition(change.position) ?: continue
                    val doubt = preview.doubts.firstOrNull { position in it.start until it.end }
                        ?: hints.firstOrNull { position in it.start until it.end }?.let { CaptureDoubt(it.start, it.end, it.text, "confirmed") }
                    if (doubt != null) open(doubt)
                }
            }
        }
    }
}

@Composable internal fun CapturePreviewPanel(state: CaptureUiState, actions: CaptureActions, preview: CapturePreview) {
    val hints = state.captureHints
    val private = state.privateCapture
    val busy = state.aiPreviewBusy
    val ai = state.aiDraftPreview
    val error = state.aiPreviewError
    val selectedKind = state.captureKind
    val deadline = state.captureDeadline
    var aiConsent by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    if (preview.records.isEmpty()) return
    Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp).testTag("capture-preview")) {
        for ((index, record) in preview.records.take(3).withIndex()) {
            val timing = record.startAt?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ') }
                ?: record.dueDate?.let { "Deadline $it" } ?: record.plannedFor?.let { "Planned $it" } ?: if (record.baseKind == "event") "Needs day and time" else "No scheduled time"
            val shownTiming = if (record.baseKind == "task" && record.dueDate != null) "Deadline ${record.startAt?.let { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ') } ?: record.dueDate}" else timing
            if (record.startAt != null || record.dueDate != null || record.plannedFor != null) Text(shownTiming, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("preview-record-$index"))
            if (preview.records.size == 1) {
                if (record.kindChoices.orEmpty().isNotEmpty()) IntentChoiceButtons(state, actions)
                if (record.baseKind == "event" && record.time != null && record.date == null) {
                    Row {
                        TextButton({ actions.chooseDay(record, LocalDate.now()) }, Modifier.testTag("event-today")) { Text("Today at ${record.time}") }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        TextButton({ val day = LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> actions.chooseDay(record, LocalDate.of(y, m + 1, d)) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }, Modifier.testTag("event-choose-date")) { Text("Choose date…") }
                    }
                }
                if (record.kindChoices.orEmpty().isEmpty()) Box {
                    TextButton({ typeMenu = true }, Modifier.testTag("change-preview-kind"), contentPadding = PaddingValues(0.dp)) { Text(record.baseKind.replaceFirstChar { it.uppercase() } + " ⌄") }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        val choices = (if (record.time == null) listOf("Task · Someday" to "task_unscheduled") else emptyList()) + listOf("Idea" to "idea", "Event" to "event", "Information" to "information")
                        for ((label, kind) in choices) DropdownMenuItem({ Text(label) }, { typeMenu = false; actions.selectKind(kind) })
                    }
                }
            }
        }
        if (selectedKind == "task_deadline") DateControl("Task deadline", deadline) { if (it == null) actions.selectKind("task_deadline") else actions.chooseIntent("task_deadline", it) }
        for ((index, doubt) in preview.doubts.take(4).withIndex()) TextButton({ actions.showClarification(doubt) }, Modifier.testTag("capture-doubt-$index").semantics { contentDescription = "Clarify ${doubt.text}: ${doubtMessage(doubt.reason)}" }, contentPadding = PaddingValues(0.dp)) {
            Text(if (doubt.reason == "missing_date") "Date…" else if (doubt.reason == "missing_time") "Time…" else "${doubt.text.take(24)} ⌄", color = MaterialTheme.colorScheme.secondary)
        }
        if (hints.clarifications.isNotEmpty()) TextButton(actions.resetClarifications, contentPadding = PaddingValues(0.dp)) { Text("Reset clarifications") }
        if (preview.records.singleOrNull()?.calendarEligible == true) TextButton(actions.createEvent, Modifier.testTag("create-preview-event"), contentPadding = PaddingValues(0.dp)) { Text("Save as calendar event") }
        if (!private) TextButton({ aiConsent = true }, enabled = !busy, modifier = Modifier.testTag("preview-ai"), contentPadding = PaddingValues(0.dp)) { Text(if (busy) "…" else "AI preview") }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        ai?.let { value ->

            for (record in value.records) Text("${record.kind}: ${record.title}", style = MaterialTheme.typography.bodySmall)
            if (value.needsReview) Text("Needs clarification or review before automatic application", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (aiConsent) AlertDialog(onDismissRequest = { aiConsent = false }, title = { Text("Send this draft for AI preview?") }, text = { Text("The current text and its clarifications will be sent to your backend and AI provider now. This uses one AI request and creates no note or calendar event. The server retains an account-scoped interpretation example. Service-wide contribution is a separate optional setting. Editing invalidates the preview.") }, confirmButton = { TextButton({ aiConsent = false; actions.requestAiPreview() }) { Text("Send for preview") } }, dismissButton = { TextButton({ aiConsent = false }) { Text("Cancel") } })
}

internal fun doubtMessage(reason: String) = when (reason) {
    "time_or_day" -> "Time or day of the month?"
    "ambiguous_date" -> "Choose the full date"
    "ambiguous_weekday" -> "Which date?"
    "missing_date" -> "Choose the day"
    "missing_time" -> "Choose the time"
    "invalid_time" -> "Check this time"
    "invalid_date" -> "Check this date"
    "clock_change" -> "Clock change: choose an unambiguous time"
    "confirmed" -> "Edit your clarification"
    else -> "Choose day and time"
}

@Composable internal fun CaptureClarificationDialog(state: CaptureUiState, actions: CaptureActions) {
    val doubt = state.clarification
    val draft = state.draft
    val hints = state.captureHints
    val current = doubt ?: return
    val pending = state.pendingSubmission
    if (current.end > draft.length || draft.substring(current.start, current.end) != current.text) return
    key(current.start, current.end, draft) {
        val context = LocalContext.current
        val existing = hints.clarifications.firstOrNull { it.start == current.start && it.end == current.end }
        val base = state.clarificationSeed
        var day by remember { mutableStateOf(existing?.date ?: base?.date) }
        var clock by remember { mutableStateOf(existing?.time ?: base?.time) }
        var literal by remember { mutableStateOf(existing?.kind == "literal") }
        val hour = current.text.toIntOrNull()?.takeIf { it in 0..23 }
        val kind = if (literal) "literal" else if (day != null && clock != null) "datetime" else if (day != null) "date" else "time"
        val choice = CaptureHint(current.start, current.end, current.text, kind, day.takeUnless { literal }, clock.takeUnless { literal })
        val candidateHints = InterpretationHints(clarifications = hints.clarifications.filterNot { it.start < choice.end && it.end > choice.start } + choice)
        val candidate by produceState<CapturePreview?>(null, day, clock, literal, draft) {
            value = withContext(Dispatchers.Default) { runCatching { actions.preview(candidateHints) }.getOrNull() }
        }
        AlertDialog(onDismissRequest = actions.cancelClarification, title = { Text("Clarify “${current.text.take(36)}”") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(doubtMessage(current.reason))
                if (hour != null) TextButton({ day = LocalDate.now().toString(); clock = "%02d:00".format(hour); literal = false }, Modifier.testTag("clarify-today-hour")) { Text("Today at %02d:00".format(hour)) }
                DateControl("Date", day) { day = it; literal = false }
                if (clock != null) TextButton({ day = LocalDate.now().toString(); literal = false }, Modifier.testTag("clarify-known-time-today")) { Text("Today at $clock") }
                if (current.text.toIntOrNull()?.let { it in 1..31 } == true) TextButton({
                    val now = LocalDate.now(); val target = now.withDayOfMonth(minOf(current.text.toInt(), now.lengthOfMonth()))
                    DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString(); literal = false }, target.year, target.monthValue - 1, target.dayOfMonth).show()
                }) { Text("Day ${current.text} of a month…") }
                Row {
                    TextButton({
                        val initial = clock?.let(LocalTime::parse) ?: hour?.let { LocalTime.of(it, 0) } ?: LocalTime.NOON
                        TimePickerDialog(context, { _, h, m -> clock = LocalTime.of(h, m).toString(); literal = false }, initial.hour, initial.minute, true).show()
                    }, Modifier.testTag("clarify-time")) { Text("Time: ${clock ?: "Not set"}") }
                    if (clock != null) TextButton({ clock = null }) { Text("Clear time") }
                }
                if (current.reason in setOf("time_or_day", "confirmed")) TextButton({ literal = true; day = null; clock = null }, Modifier.testTag("clarify-literal")) { Text("This number is not a date or time") }
                candidate?.let { result ->
                    Text("Will be understood as:", style = MaterialTheme.typography.labelLarge)
                    for (record in result.records) Text("${record.baseKind} · ${record.date ?: "day not set"} · ${record.time ?: "time not set"}", style = MaterialTheme.typography.bodySmall)
                    if (result.doubts.isNotEmpty()) Text("Still needs clarification: ${result.doubts.map { doubtMessage(it.reason) }.distinct().joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ actions.cancelClarification(); actions.save(null, true) }, Modifier.testTag("save-note-without-event")) { Text("Save original note without creating an event") }
            }
        }, confirmButton = { TextButton({ actions.clarify(choice) }, enabled = literal || day != null || clock != null, modifier = Modifier.testTag("apply-clarification")) { Text(if (pending == null) "Apply clarification" else if (candidate?.doubts?.isEmpty() == true) when (candidate?.records?.singleOrNull()?.baseKind) { "event" -> "Save event"; "task" -> "Save task"; else -> "Save items" } else "Continue clarification") } }, dismissButton = { TextButton(actions.cancelClarification) { Text("Keep editing") } })
    }
}

@Composable private fun IntentChoiceButtons(state: CaptureUiState, actions: CaptureActions, tagPrefix: String = "intent") {
    val context = LocalContext.current
    Column {
        Row {
            TextButton({ val day = LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> actions.chooseIntent("task_deadline", LocalDate.of(y, m + 1, d).toString()) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }, Modifier.testTag("$tagPrefix-deadline")) { Text("Deadline") }
            TextButton({ actions.chooseIntent("task_unscheduled", null) }, Modifier.testTag("$tagPrefix-someday")) { Text("Someday") }
            TextButton({ actions.chooseIntent("idea", null) }, Modifier.testTag("$tagPrefix-idea")) { Text("Idea") }
        }
    }
}

@Composable internal fun CaptureIntentDialog(state: CaptureUiState, actions: CaptureActions) {
    val showing = state.choosingIntent
    if (!showing) return
    val preview = state.capturePreview
    AlertDialog(onDismissRequest = actions.cancelClarification, title = { Text("How would you like to keep this?") }, text = {
        Column {
            if (preview.records.size == 1) IntentChoiceButtons(state, actions, "submit-intent")
            else if (preview.records.isEmpty()) Text("Reading the local preview…")
            else Text("This draft contains several items. Edit it to choose each item separately, or save the original only.")
        }
    }, confirmButton = { TextButton({ actions.cancelClarification(); actions.save(null, true) }) { Text("Save original only") } }, dismissButton = { TextButton(actions.cancelClarification) { Text("Keep editing") } })
}
