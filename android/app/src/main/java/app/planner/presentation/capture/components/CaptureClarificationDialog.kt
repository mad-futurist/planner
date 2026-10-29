package app.planner.presentation.capture.components

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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import app.planner.domain.interpretation.CaptureHint
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.interpretation.InterpretationHints
import app.planner.presentation.capture.CaptureActions
import app.planner.presentation.capture.CaptureUiState
import app.planner.presentation.common.editor.DateControl
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
