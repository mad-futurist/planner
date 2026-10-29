package app.planner.presentation.capture.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import app.planner.presentation.capture.CaptureActions
import app.planner.presentation.capture.CaptureUiState
import java.time.*

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
