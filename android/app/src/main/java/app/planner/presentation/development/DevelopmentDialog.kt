package app.planner.presentation.development

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.google.gson.*
import java.time.*

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
