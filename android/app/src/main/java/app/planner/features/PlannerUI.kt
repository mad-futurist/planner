package app.planner.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import app.planner.core.designsystem.*
import app.planner.core.model.*

@Composable fun PlannerUI(vm: PlannerViewModel) {
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route
    val settings by vm.settings.collectAsStateWithLifecycle()
    val private by vm.privateCapture.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val processing by vm.processing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val consent by vm.cloudExplanation.collectAsStateWithLifecycle()
    val status=when {
        private -> "Private mode"
        metadata["network"]=="offline" -> "AI offline"
        metadata["network"]=="unauthorized" -> "Reconnect AI"
        metadata["ai"]=="unconfigured" -> "AI unavailable"
        metadata["network"]!="connected" -> "AI not connected"
        processing.getOrDefault("review",0)>0 -> "Review needed"
        processing.getOrDefault("failed",0)>0 -> "AI needs attention"
        processing.getOrDefault("pending",0)>0 -> "AI organizing"
        else -> "Notes organized"
    }
    val snackbar=remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingId by rememberSaveable { mutableStateOf<String?>(null) }
    var developingId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus=remember { FocusRequester() }
    val captureFocus by vm.focusCapture.collectAsStateWithLifecycle()
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(captureFocus) { if(captureFocus>0) { focus.requestFocus(); keyboard?.show() } }
    fun go(target:String) {
        if(target=="profile" && nav.popBackStack("profile",false)) return
        nav.navigate(target) { popUpTo(nav.graph.findStartDestination().id) { saveState=true }; launchSingleTop=true; restoreState=target!="profile" }
    }
    fun develop(id:String) { developingId=id; vm.action { vm.repository.dao.recordById(id)?.let { vm.sync.develop(it) } } }
    LaunchedEffect(message) {
        message?.let {
            val result=snackbar.showSnackbar(it,actionLabel=if(vm.undoId.value!=null) "Undo" else null,withDismissAction=vm.undoId.value!=null,duration=SnackbarDuration.Short)
            if(result==SnackbarResult.ActionPerformed) vm.undo()
            vm.undoId.value=null; vm.message.value=null
        }
    }
    PlannerTheme(settings["appearance"] ?: "system") {
        val ime=WindowInsets.ime.getBottom(LocalDensity.current)>0
        Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.statusBars,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                CaptureBar(vm,focus)
                if(!ime) PlannerBottomNavigation(route,::go)
            }
        }) { padding ->
            NavHost(nav,"today",Modifier.padding(padding)) {
                composable("today") { TodayScreen(vm,nav,status,{editingId=it},::develop) { vm.captureKind.value="task"; focus.requestFocus(); keyboard?.show() } }
                composable("calendar") { CalendarScreen(vm,nav,status) {editingId=it} }
                composable("tasks") { TasksScreen(vm,nav,status,{editingId=it}) { vm.captureKind.value="task_unscheduled"; focus.requestFocus(); keyboard?.show() } }
                composable("ideas") { IdeasScreen(vm,nav,status,{editingId=it},::develop) }
                composable("profile") { ProfileScreen(vm,nav) }
                composable("journal") { JournalScreen(vm,nav,{editingId=it},{reviewingId=it}) }
                composable("triage") { TriageScreen(vm,nav,{editingId=it},{reviewingId=it}) }
                composable("settings") { SettingsScreen(vm,nav) }
            }
        }
        editingId?.let { id -> key(id) { val record by produceState<Record?>(null,id) { value=vm.repository.dao.recordById(id) }; record?.let { RecordEditor(vm,it,{related->editingId=related}) {editingId=null} } } }
        reviewingId?.let { id -> val note by produceState<RawNote?>(null,id) { value=vm.repository.dao.noteById(id) }; note?.let { InterpretationEditor(vm,it) {reviewingId=null} } }
        developingId?.let { id -> val record by produceState<Record?>(null,id) { value=vm.repository.dao.recordById(id) }; record?.let { DevelopmentDialog(vm,it) {developingId=null} } }
        if(consent) AlertDialog(onDismissRequest={vm.cloudExplanation.value=false},title={Text("Choose how your thoughts are organized")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Private: notes stay on this device. You can organize them yourself, even offline.")
            Text("Cloud AI: new notes, relevant corrections and type context are sent to your configured server and its AI provider for interpretation. This can include sensitive text. Provider retention depends on your chosen service.")
            Text("Nothing is uploaded by this choice alone. Future Cloud AI captures may queue offline and upload when connected. Existing private notes stay private. You can switch back before any capture.")
        }},confirmButton={TextButton({vm.allowCloud()},Modifier.testTag("allow-cloud")) {Text("Allow cloud AI for new notes")}},dismissButton={TextButton({vm.cloudExplanation.value=false}) {Text("Keep private")}})
    }
}

@Composable private fun CaptureBar(vm:PlannerViewModel, focus:FocusRequester) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val private by vm.privateCapture.collectAsStateWithLifecycle()
    val kind by vm.captureKind.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,shadowElevation=2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box { CircleAction(Glyph.Plus,"Choose capture type",{menu=true},primary=true)
                    DropdownMenu(menu,{menu=false}) {
                        for((label,value) in listOf((if(private) "Note · saved to Journal" else "Note · AI organizes") to null,"Task for today" to "task","Task · no date" to "task_unscheduled","Idea" to "idea","Event" to "event","Information" to "information")) DropdownMenuItem({Text(label)},{vm.captureKind.value=value;menu=false;focus.requestFocus()})
                    }
                }
                BasicTextField(draft,vm::setDraft,Modifier.weight(1f).heightIn(min=52.dp,max=120.dp).padding(horizontal=8.dp,vertical=15.dp).focusRequester(focus).testTag("capture"),textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),maxLines=4,decorationBox={inner -> if(draft.isEmpty()) Text("Write anything…",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant); inner()})
                FilledIconButton({vm.capture(kind)},enabled=draft.isNotBlank() && !saving,modifier=Modifier.size(48.dp).testTag("send").semantics {contentDescription="Save note"},colors=IconButtonDefaults.filledIconButtonColors(disabledContainerColor=MaterialTheme.colorScheme.primary,disabledContentColor=MaterialTheme.colorScheme.onPrimary)) { if(saving) Text("…") else PlannerIcon(Glyph.ArrowUp,Modifier.size(24.dp),MaterialTheme.colorScheme.onPrimary) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=40.dp).clickable {vm.setPrivacy(!private)}.testTag("privacy").padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                PlannerIcon(if(private) Glyph.Shield else Glyph.Sparkles,Modifier.size(13.dp))
                Text(if(private) "Private · on this device" else "Cloud AI · new notes may upload",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(kind!=null) Text(when(kind) {"task"->"Task · Today";"task_unscheduled"->"Task · No date";else->kind!!.replaceFirstChar {it.uppercase()}},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                PlannerIcon(Glyph.Down,Modifier.size(12.dp))
            }
        }
    }
}

@Composable internal fun ScreenHeader(title:String,nav:NavHostController,subtitle:String?=null,status:String?=null) {
    val backAction: (() -> Unit)? = if(title in setOf("Settings","Journal","Review")) { {nav.popBackStack()} } else null
    EditorialHeader(title,subtitle,status,{nav.navigate("settings")},{nav.navigate("profile")},backAction)
}

@Composable internal fun LocalQueryHint(vm:PlannerViewModel,key:String,emptyText:String) {
    val reads by vm.localReads.collectAsStateWithLifecycle()
    when(reads[key]) {
        "ready"->EmptyState(emptyText)
        "error"->{EmptyState("Couldn't read saved data. Your input is still here.");TextButton(vm::reloadLocalQueries) {Text("Try again")}}
        else->EmptyState("Reading saved data…")
    }
}

@Composable internal fun TaskRow(vm:PlannerViewModel,record:Record,compact:Boolean=false,open:()->Unit) {
    TaskCard(record,{if(record.state=="completed") vm.action {vm.repository.complete(record,true)} else vm.finish(record)},open,compact)
}
