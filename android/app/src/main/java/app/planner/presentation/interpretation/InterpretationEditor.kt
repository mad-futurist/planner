package app.planner.presentation.interpretation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import app.planner.presentation.common.editor.DateControl
import app.planner.presentation.common.editor.KindPicker
import com.google.gson.*
import java.time.*

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
