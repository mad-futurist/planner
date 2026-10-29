package app.planner.features

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import app.planner.domain.model.*
import com.google.gson.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun InterpretationEditor(state: InterpretationUiState, save: (String, String, String?) -> Unit, close: () -> Unit) {
    val note = state.note ?: return
    var title by rememberSaveable(note.id) { mutableStateOf(note.content.take(1000)) }
    var kind by rememberSaveable(note.id) { mutableStateOf("task") }
    var planned by rememberSaveable(note.id) { mutableStateOf<String?>(null) }
    val history = state.history
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ save(kind, title, planned) }, enabled = title.isNotBlank() && !state.saving) { Text("Save") } }, dismissButton = { TextButton(close) { Text("Cancel") } })
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

@Composable internal fun DevelopmentDialog(state: DevelopmentUiState, approve: (List<String>) -> Unit, retry: () -> Unit, close: () -> Unit) {
    val record = state.record ?: return
    val status = state.status
    val steps = state.steps.takeIf { state.error == null }.orEmpty()
    var selected by remember(state.jobId, steps) { mutableStateOf(steps.toSet()) }
    val applying = state.saving
    AlertDialog(onDismissRequest = close, title = { Text("Develop this idea") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(record.title)
            if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error) else when (status) {
                "queued", "running" -> Text("Waiting for AI suggestions. You can keep capturing thoughts.", Modifier.padding(top = 12.dp))
                "complete" -> { Text("Choose what to turn into tasks.", Modifier.padding(vertical = 12.dp)); for (step in steps) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(step in selected, { checked -> selected = if (checked) selected + step else selected - step }); Text(step, Modifier.weight(1f)) } }
                "unconfigured" -> Text("The backend AI provider is not configured. Your idea is saved; add next steps manually or configure the provider and retry.")
                "failed", "stale" -> Text("Suggestions couldn't be used. Your idea is safe. Try again when you're ready.")
                else -> Text("AI isn't available here yet. Check your connection and AI settings; private ideas remain on this device.")
            }
        }
    }, confirmButton = {
        if (steps.isNotEmpty()) TextButton({ approve(steps.filter { it in selected }) }, enabled = selected.isNotEmpty() && !applying) { Text("Create ${selected.size} tasks") }
        else TextButton({ retry() }) { Text("Try again") }
    }, dismissButton = { TextButton(close) { Text("Close") } })
}
