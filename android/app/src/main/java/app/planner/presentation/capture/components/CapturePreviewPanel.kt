package app.planner.presentation.capture.components

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import app.planner.domain.interpretation.CapturePreview
import app.planner.presentation.capture.CaptureActions
import app.planner.presentation.capture.CaptureUiState
import app.planner.presentation.common.editor.DateControl
import java.time.*

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

@Composable internal fun IntentChoiceButtons(state: CaptureUiState, actions: CaptureActions, tagPrefix: String = "intent") {
    val context = LocalContext.current
    Column {
        Row {
            TextButton({ val day = LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> actions.chooseIntent("task_deadline", LocalDate.of(y, m + 1, d).toString()) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }, Modifier.testTag("$tagPrefix-deadline")) { Text("Deadline") }
            TextButton({ actions.chooseIntent("task_unscheduled", null) }, Modifier.testTag("$tagPrefix-someday")) { Text("Someday") }
            TextButton({ actions.chooseIntent("idea", null) }, Modifier.testTag("$tagPrefix-idea")) { Text("Idea") }
        }
    }
}
