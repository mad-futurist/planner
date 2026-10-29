package app.planner.features

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import app.planner.core.designsystem.*
import app.planner.domain.model.*

@Composable fun PlannerUI(vm: PlannerViewModel, capture: CaptureViewModel) {
    val shell by vm.uiState.collectAsStateWithLifecycle()
    val state by capture.uiState.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val status = when {
        state.privateCapture -> "Private"
        shell.metadata["network"] == "offline" -> "Offline"
        shell.metadata["network"] == "unauthorized" -> "Reconnect"
        shell.metadata["ai"] == "unconfigured" -> "AI unavailable"
        shell.metadata["network"] != "connected" -> "Not connected"
        shell.processing.getOrDefault("review", 0) > 0 -> "Review needed"
        shell.processing.getOrDefault("pending", 0) > 0 -> "Organizing…"
        else -> null
    }
    val actions = remember(capture) { CaptureActions(capture::setDraft, capture::selectCaptureKind, capture::capture, capture::setPrivacy, capture::showClarification, capture::chooseEventDay, capture::chooseIntent, capture::resetClarifications, capture::createEvent, capture::requestAiPreview, capture::cancelClarification, capture::clarify, capture::interpretationPreview) }
    val snackbar = remember { SnackbarHostState() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewingId by rememberSaveable { mutableStateOf<String?>(null) }
    var developingId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.focusCapture) { if (state.focusCapture > 0) { focus.requestFocus(); keyboard?.show() } }
    fun addTask(kind: String) { capture.selectCaptureKind(kind); capture.focus() }
    fun go(target: String) {
        if (target == "profile" && nav.popBackStack("profile", false)) return
        nav.navigate(target) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = target != "profile" }
    }
    LaunchedEffect(shell.message?.id) {
        shell.message?.let { message ->
            val result = snackbar.showSnackbar(message.text, actionLabel = if (message.undoRecordId != null) "Undo" else null, withDismissAction = message.undoRecordId != null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undo(message) else vm.dismissMessage(message.id)
        }
    }
    PlannerTheme(shell.settings["appearance"] ?: "system") {
        val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.statusBars, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CaptureBar(state, actions, focus)
                if (!ime) PlannerBottomNavigation(route, ::go)
            }
        }) { padding ->
            PlannerNavHost(nav, Modifier.padding(padding), status, { editingId = it }, { reviewingId = it }, { developingId = it }, ::addTask, capture)
        }
        editingId?.let { id -> key(id) { RecordRoute(id, { editingId = it }, close = { editingId = null }) } }
        reviewingId?.let { id -> key(id) { InterpretationRoute(id, close = { reviewingId = null }) } }
        developingId?.let { id -> key(id) { DevelopmentRoute(id, close = { developingId = null }) } }
        CaptureClarificationDialog(state, actions)
        CaptureIntentDialog(state, actions)
        if (state.cloudExplanation) AlertDialog(onDismissRequest = capture::dismissCloudExplanation, title = { Text("Enable Cloud AI?") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New Cloud AI notes, relevant corrections and type context are sent to your configured server and AI provider. This can include sensitive text. Provider retention depends on your chosen service.")
            Text("The server retains account interpretation history. Service-wide contribution is a separate choice in Settings. Existing private notes stay on this device. You can switch back before saving.")
        } }, confirmButton = { TextButton(capture::allowCloud, Modifier.testTag("allow-cloud")) { Text("Enable Cloud AI") } }, dismissButton = { TextButton(capture::dismissCloudExplanation) { Text("Keep private") } })
    }
}
