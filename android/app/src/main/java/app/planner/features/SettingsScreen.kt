package app.planner.features

import app.planner.navigation.ScreenNavigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import app.planner.core.designsystem.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.planner.BuildConfig
import app.planner.features.overlay.OverlayControls

@Composable internal fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, private: Boolean, changePrivacy: (Boolean)->Unit, nav: ScreenNavigation) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val compactHeader = density.fontScale > 1.2f || androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width / density.density < 380f
    val settings = state.settings
    val metadata = state.metadata
    val outbox = state.outbox
    var server by rememberSaveable { mutableStateOf(settings["serverUrl"] ?: BuildConfig.DEFAULT_BACKEND_URL.ifBlank { if (BuildConfig.DEBUG) "http://10.0.2.2:8000" else "" }) }
    var invitation by rememberSaveable { mutableStateOf("") }
    val connecting = "connect" in state.busy
    var clearing by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<app.planner.domain.model.OutboxOperation?>(null) }
    val exporting = "export" in state.busy
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.export { context.contentResolver.openOutputStream(uri)?.bufferedWriter() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("settings-list"),contentPadding=PaddingValues(top=10.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Box {if(!compactHeader) Landscape(Modifier.align(Alignment.BottomEnd).width(150.dp).height(80.dp),sunrise=true);ScreenHeader("Settings",nav,"Make the app work for you.")}}
        item {OverlayControls(settings, actions.setPreference, actions.report)}
        item {SettingsGroup("AI ORGANIZATION","AI organization & connection","Choose where your thoughts are interpreted.",Glyph.Sparkles) {
            Text("Cloud AI sends eligible notes to your configured backend and its provider. Private captures stay here.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(server,{server=it},label={Text("Backend URL")},singleLine=true,modifier=Modifier.fillMaxWidth())
            AccountControls(state, actions, server)
            DatasetControls(state, actions)
            Text("Advanced: operator invitation",style=MaterialTheme.typography.labelSmall)
            OutlinedTextField(invitation,{invitation=it},label={Text("One-time invitation")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Button({actions.connect(server, invitation)},enabled=server.isNotBlank() && invitation.isNotBlank() && !connecting) {Text(if(connecting) "Connecting…" else "Connect")}
            Text("Connection: ${metadata["network"]?:"Not connected"} · AI: ${metadata["ai"]?:"Not configured"}",style=MaterialTheme.typography.bodySmall)
            TextButton({actions.sync()}) {Text("Sync now")}
        }}
        item {SettingsGroup("PRIVACY","Privacy & data","Your notes, your choice of where they go.",Glyph.Shield) {
            Text("Notes are saved on this device first. Private notes never enter the upload queue. No clipboard or screen-content monitoring.",style=MaterialTheme.typography.bodySmall)
            TextButton({changePrivacy(!private)}) {Text(if(private) "Private mode · learn about cloud AI" else "Switch new captures to private")}
            Text("Changing mode does not recall notes you previously allowed to upload.",style=MaterialTheme.typography.bodySmall)
        }}
        item {SettingsGroup("APPEARANCE","Appearance","A comfortable view, light or dark.",Glyph.Palette) {
            for((key,label) in listOf("system" to "Follow system","light" to "Light","dark" to "Dark")) Row(Modifier.fillMaxWidth().clickable {actions.setPreference("appearance",key)},verticalAlignment=Alignment.CenterVertically) {RadioButton((settings["appearance"]?:"system")==key,{actions.setPreference("appearance",key)});Text(label)}
        }}
        item {SettingsGroup("CALENDAR","Calendar behavior","Set the first day of your week.",Glyph.Calendar) {
            for((key,label) in listOf("sunday" to "Sunday","monday" to "Monday")) Row(Modifier.fillMaxWidth().clickable {actions.setPreference("weekStart",key)},verticalAlignment=Alignment.CenterVertically) {RadioButton((settings["weekStart"]?:"sunday")==key,{actions.setPreference("weekStart",key)});Text(label)}
            Text("Deadlines and planned days stay separate from appointments. Calendar data stays within Planner.",style=MaterialTheme.typography.bodySmall)
        }}
        item {SettingsGroup("DATA & EXPORT","Data & export","Export your notes or clear this device.",Glyph.Download) {
            TextButton({export.launch("planner-export-${java.time.LocalDate.now()}.json")},enabled=!exporting) {Text(if(exporting) "Exporting…" else "Export local notes and records")}
            TextButton({clearing=true},enabled=!exporting) {Text("Clear local data")}
            Text("Export excludes credentials. Clearing this device does not remove server copies.",style=MaterialTheme.typography.bodySmall)
        }}
        item {SettingsGroup("DIAGNOSTICS","Connection & diagnostics","Saved locally first, with visible sync state.",Glyph.Clock) {
            Text("Planner ${BuildConfig.VERSION_NAME}\nLast local capture: ${metadata["lastCaptureMs"]?:"—"} ms\nLast sync: ${metadata["lastSync"]?:"Not yet"}\nUnsent changes shown: ${outbox.size}",style=MaterialTheme.typography.bodySmall)
        }}
        if(outbox.any {it.status!="queued"}) item {Text("Changes needing attention",style=MaterialTheme.typography.titleMedium)}
        items(outbox.filter {it.status!="queued"},key={it.id}) {operation->PlannerCard {Column(Modifier.padding(14.dp)) {
            Text(operation.lastError?:"Change not synchronized",style=MaterialTheme.typography.bodySmall)
            if(operation.status=="conflict") TextButton({conflict=operation}) {Text("Keep this device's version")}
            else if(operation.path.startsWith("records/") && operation.method=="PATCH") TextButton({conflict=operation}) {Text("Send the latest saved version")}
            else TextButton({actions.retry(operation)}) {Text("Retry")}
        }}}
        item {PlannerCard(color=MaterialTheme.colorScheme.primaryContainer) {Box(Modifier.fillMaxWidth().heightIn(min=60.dp)) {Landscape(Modifier.align(Alignment.BottomEnd).width(130.dp).height(60.dp));Column(Modifier.padding(14.dp)) {Text("A little more breathing room",style=MaterialTheme.typography.labelLarge);Text("Make this space yours.",style=MaterialTheme.typography.bodySmall)}}}}
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Clear this device?") }, text = { Text("This removes local notes, records, drafts, queued changes, and the device credential. Server copies remain. Export anything you want to keep first.") }, confirmButton = { TextButton({ actions.clear(); clearing = false  }) { Text("Clear") } }, dismissButton = { TextButton({ clearing = false }) { Text("Cancel") } })
    conflict?.let { operation -> AlertDialog(onDismissRequest = { conflict = null }, title = { Text("Keep this version?") }, text = { Text("Your local changes will be sent using the server's current revision. Previous server values are retained in correction history.") }, confirmButton = { TextButton({ actions.keepLocal(operation); conflict = null }) { Text("Keep local changes") } }, dismissButton = { TextButton({ conflict = null }) { Text("Cancel") } }) }
}

@Composable internal fun SettingsGroup(label:String,title:String,subtitle:String,glyph:Glyph,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    PlannerCard {
        Column(Modifier.fillMaxWidth().clickable {expanded=!expanded}.padding(14.dp)) {
            Text(label,style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(color=if(glyph==Glyph.Sparkles || glyph==Glyph.Download) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,shape=CircleShape) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {PlannerIcon(glyph,Modifier.size(22.dp))}}
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                PlannerIcon(if(expanded) Glyph.Up else Glyph.Right,Modifier.size(16.dp))
            }
        }
        if(expanded) Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
