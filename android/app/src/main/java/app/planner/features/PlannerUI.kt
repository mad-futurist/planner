package app.planner.features

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
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
import androidx.core.view.WindowCompat

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
    val captureFocus by vm.focusCapture.collectAsStateWithLifecycle()
    fun openComposer(kind: String? = null) {
        vm.captureKind.value = kind
        nav.navigate("capture") { launchSingleTop = true }
    }
    LaunchedEffect(captureFocus) { if (captureFocus > 0 && route != "capture") openComposer() }
    fun go(target:String) {
        if (target == "today") {
            nav.popBackStack("today", inclusive = false, saveState = true)
            return
        }
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
        val dark = plannerDarkAppearance(settings["appearance"] ?: "system")
        val todayStyle = todayColors(dark)
        val warm = todayStyle.takeIf { route == null || route in setOf("today", "calendar", "tasks", "ideas", "profile") }
        val activity = LocalActivity.current
        SideEffect {
            activity?.window?.let { window ->
                val insets = WindowCompat.getInsetsController(window, window.decorView)
                insets.isAppearanceLightStatusBars = !dark
                insets.isAppearanceLightNavigationBars = !dark
            }
        }
        Scaffold(
            modifier = if (warm != null) Modifier.background(warm.canvas).todayBackdrop(warm) else Modifier,
            containerColor = if (warm != null) Color.Transparent else MaterialTheme.colorScheme.background,
            contentWindowInsets = if (route == "capture") WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
            snackbarHost={ SnackbarHost(snackbar) },
            bottomBar = {
                if (route != "capture") {
                    Column(Modifier.imePadding()) {
                        Box(Modifier.fillMaxWidth().then(if (ime || warm == null) Modifier else Modifier.todayComposerArt(todayStyle))) {
                            CollapsedComposer(vm, todayStyle) { openComposer() }
                        }
                        if (!ime) PlannerBottomNavigation(route, ::go, todayStyle)
                    }
                }
            }
        ) { padding ->
            NavHost(nav,"today",Modifier.padding(padding)) {
                composable("today") {
                    TodayScreen(
                        vm, nav, status, todayStyle, { editingId = it }, ::develop,
                        addTask = { openComposer("task") },
                        openProfile = { go("profile") },
                        openCollection = { go("ideas") },
                    )
                }
                composable("calendar") { CalendarScreen(vm,nav,todayStyle) {editingId=it} }
                composable("tasks") { TasksScreen(vm,nav,todayStyle,{editingId=it}) { openComposer("task_unscheduled") } }
                composable("ideas") { IdeasScreen(vm,nav,todayStyle,{editingId=it},::develop) { openComposer("idea") } }
                composable("profile") { ProfileScreen(vm,nav,todayStyle) }
                composable("journal") { JournalScreen(vm,nav,{editingId=it},{reviewingId=it}) }
                composable("triage") { TriageScreen(vm,nav,{editingId=it},{reviewingId=it}) }
                composable("settings") { SettingsScreen(vm,nav) }
                composable("capture") { captureEntry ->
                    SemanticComposer(vm, todayStyle) {
                        if (nav.currentBackStackEntry?.id == captureEntry.id) nav.popBackStack()
                    }
                }
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
