package app.planner.features

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable internal fun DatasetControls(state: SettingsUiState, actions: SettingsActions) {
    val metadata = state.metadata
    val owner = metadata["owner"] ?: return
    val enabled = state.operations.contribution
    val busy = "contribution" in state.busy
    val error = state.operations.contributionError
    var explain by remember(owner) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Interpretation history & improvement", style = MaterialTheme.typography.titleSmall)
        Text("The server keeps cloud interpretations and edits in your account history. Private notes stay on this device.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Contribute cloud examples", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled == true, enabled = enabled != null && !busy, onCheckedChange = { if (it) explain = true else actions.contribution(false) }, modifier = Modifier.semantics { contentDescription = "Contribute cloud examples for service improvement" })
        }
        Text(if (busy) "Loading your choice…" else if (enabled == null) "Contribution status is unavailable until you connect to the server." else if (enabled == true) "Your cloud text, interpretations and edits may be selected for service improvement." else "Examples remain scoped to your account. Service-wide contribution is off.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall); TextButton({ actions.loadContribution() }, enabled = !busy) { Text("Retry") } }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("Contribute interpretation examples?") }, text = {
        Text("This allows your existing and future cloud note text, AI interpretations, clarifications and edits to be included in datasets used to improve this service. Text may contain personal details. Private notes are excluded. You can turn this off to exclude your account from future exports; already exported copies need separate handling by the service operator. No training runs automatically.")
    }, confirmButton = { TextButton({ explain = false; actions.contribution(true) }) { Text("Allow contribution") } }, dismissButton = { TextButton({ explain = false }) { Text("Keep account-only") } })
}
