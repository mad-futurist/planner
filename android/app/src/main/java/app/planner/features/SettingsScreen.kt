package app.planner.features

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.planner.BuildConfig
import app.planner.sync.SyncScheduler
import app.planner.features.overlay.OverlayControls
import app.planner.features.overlay.OverlayManager
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable internal fun SettingsScreen(vm: PlannerViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val outbox by vm.outbox.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(settings["serverUrl"] ?: if (BuildConfig.DEBUG) "http://10.0.2.2:8000" else "") }
    var invitation by rememberSaveable { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<app.planner.core.model.OutboxOperation?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.action {
            exporting = true
            try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write("{\"schemaVersion\":1,\"notes\":[")
                    var before: String? = null; var beforeId: String? = null; var first = true
                    while (true) {
                        val page = vm.repository.dao.exportPage(50, before, beforeId)
                        if (page.isEmpty()) break
                        for (note in page) {
                            if (!first) writer.write(",")
                            first = false
                            val value = vm.repository.gson.toJsonTree(note).asJsonObject
                            value.add("records", JsonArray().apply { vm.repository.dao.recordsForNote(note.id).forEach { add(vm.repository.recordJson(it)) } })
                            writer.write(value.toString())
                        }
                        before = page.last().createdAt; beforeId = page.last().id
                    }
                    writer.write("]}")
                } ?: error("Export destination unavailable")
            }
            vm.message.value = "Export saved"
            } finally { exporting = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Settings", nav, "Make this space work for you.") }
        item { OverlayControls(vm) }
        item { Text("AI connection", style = MaterialTheme.typography.titleMedium); Text("Eligible notes go to your backend and its AI provider. Private captures stay on this device.", style = MaterialTheme.typography.bodySmall) }
        item { OutlinedTextField(server, { server = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(invitation, { invitation = it }, label = { Text("One-time invitation") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) }
        item { Button({ connecting = true; vm.action { try { vm.sync.connect(server.trim(), invitation); invitation = ""; SyncScheduler.request(context); vm.message.value = "Connected" } finally { connecting = false } } }, enabled = server.isNotBlank() && invitation.isNotBlank() && !connecting) { Text(if (connecting) "Connecting…" else "Connect") }; Text("Connection: ${metadata["network"] ?: "Not connected"} · AI: ${metadata["ai"] ?: "Not configured"}", style = MaterialTheme.typography.bodySmall); TextButton({ vm.action { vm.sync.run() }; SyncScheduler.request(context) }) { Text("Sync now") } }
        item { HorizontalDivider(); Text("Privacy & data", style = MaterialTheme.typography.titleMedium); Text("Notes are stored on this device first. Choose Private in the capture field before sending a sensitive note. No clipboard or screen-content monitoring.", style = MaterialTheme.typography.bodySmall) }
        item { TextButton({ export.launch("planner-export-${java.time.LocalDate.now()}.json") }, enabled = !exporting) { Text(if (exporting) "Exporting…" else "Export local notes and records") }; TextButton({ clearing = true }, enabled = !exporting) { Text("Clear local data") } }
        if (outbox.any { it.status != "queued" }) item { Text("Changes needing attention", style = MaterialTheme.typography.titleMedium) }
        items(outbox.filter { it.status != "queued" }, key = { it.id }) { operation ->
            Card { Column(Modifier.padding(12.dp)) {
                Text(operation.lastError ?: "Change not synchronized", style = MaterialTheme.typography.bodySmall)
                if (operation.status == "conflict") TextButton({ conflict = operation }) { Text("Keep this device's version") }
                else if (operation.path.startsWith("records/") && operation.method == "PATCH") TextButton({ conflict = operation }) { Text("Send the latest saved version") }
                else TextButton({ vm.action { vm.repository.dao.operation(operation.copy(status = "queued", retryCount = 0, nextAttemptAt = 0)); vm.repository.requestSync() } }) { Text("Retry") }
            } }
        }
        item { HorizontalDivider(); Text("Planner ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium); Text("Last local capture: ${metadata["lastCaptureMs"] ?: "—"} ms\nLast sync: ${metadata["lastSync"] ?: "Not yet"}\nUnsent changes shown: ${outbox.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Clear this device?") }, text = { Text("This removes local notes, records, drafts, queued changes, and the device credential. Server copies remain. Export anything you want to keep first.") }, confirmButton = { TextButton({ vm.action {
        androidx.work.WorkManager.getInstance(context).cancelAllWork()
        OverlayManager.stop(context)
        app.planner.features.overlay.CaptureNotification.update(context, false)
        vm.repository.syncMutex.withLock { vm.sync.tokens.clear(); vm.repository.clear(); vm.preferences.set("draft", ""); vm.preferences.set("overlayDraft", ""); vm.preferences.set("serverUrl", ""); vm.preferences.set("overlay", "false"); vm.preferences.set("notification", "false") }
        vm.setDraft(""); vm.page.value = 0; clearing = false; vm.message.value = "Local data cleared"
    } }) { Text("Clear") } }, dismissButton = { TextButton({ clearing = false }) { Text("Cancel") } })
    conflict?.let { operation -> AlertDialog(onDismissRequest = { conflict = null }, title = { Text("Keep this version?") }, text = { Text("Your local changes will be sent using the server's current revision. Previous server values are retained in correction history.") }, confirmButton = { TextButton({ vm.action { vm.sync.keepLocal(operation); conflict = null } }) { Text("Keep local changes") } }, dismissButton = { TextButton({ conflict = null }) { Text("Cancel") } }) }
}
