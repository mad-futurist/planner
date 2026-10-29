package app.planner.features

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.planner.navigation.PlannerRoutes
import app.planner.navigation.ScreenNavigation

@Composable internal fun PlannerNavHost(nav: NavHostController, modifier: Modifier, status: String?, edit: (String)->Unit, review: (String)->Unit, develop: (String)->Unit, addTask: (String)->Unit, capture: CaptureViewModel) {
    val screenNavigation = remember(nav) { ScreenNavigation({ nav.navigate(it) }, { nav.popBackStack() }) }
    NavHost(nav, PlannerRoutes.Today, modifier) {
        composable(PlannerRoutes.Today) {
            val vm: TodayViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            TodayScreen(state, TodayActions(vm::complete, vm::later, vm::selectDate, { capture.setPrivacy(false) }, vm::reloadLocalQueries), screenNavigation,status.orEmpty(),edit,develop,{addTask("task")})
        }
        composable(PlannerRoutes.Calendar) {
            val vm: CalendarViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            CalendarScreen(state, CalendarActions(vm::complete, vm::selectDate, vm::reloadLocalQueries), screenNavigation,status.orEmpty(),edit)
        }
        composable(PlannerRoutes.Tasks) {
            val vm: TasksViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            TasksScreen(state, TasksActions(vm::complete, vm::filterTasks, vm::page, vm::reloadLocalQueries), screenNavigation,status.orEmpty(),edit,{addTask("task_unscheduled")})
        }
        composable(PlannerRoutes.Ideas) {
            val vm: IdeasViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            IdeasScreen(state, IdeasActions(vm::convert, vm::search, vm::page, vm::reloadLocalQueries), screenNavigation,status.orEmpty(),edit,develop)
        }
        composable(PlannerRoutes.Profile) {
            val vm: ProfileViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            ProfileScreen(state, ProfileActions(vm::reloadLocalQueries), screenNavigation)
        }
        composable(PlannerRoutes.Journal) {
            val vm: JournalViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            JournalScreen(state, JournalActions(vm::reprocess, vm::page, vm::reloadLocalQueries), screenNavigation,edit,review)
        }
        composable(PlannerRoutes.Review) {
            val vm: ReviewViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            TriageScreen(state, TriageActions(vm::plan, vm::snooze, vm::archive, vm::postpone, vm::split, vm::createAction, vm::reloadLocalQueries), screenNavigation,edit,review)
        }
        composable(PlannerRoutes.Settings) { SettingsRoute(nav, capture) }
    }
}

@Composable internal fun RecordRoute(id: String, related: (String)->Unit, close: ()->Unit, vm: RecordViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.record != null) RecordEditor(state, { vm.save(it, active) }, { vm.complete(it, active) }, related, close)
    else ModalStatusDialog("Details", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
@Composable internal fun InterpretationRoute(id: String, close: ()->Unit, vm: InterpretationViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.note != null) InterpretationEditor(state, { kind, title, planned -> vm.save(kind, title, planned, active) }, close)
    else ModalStatusDialog("Organize note", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
@Composable internal fun DevelopmentRoute(id: String, close: ()->Unit, vm: DevelopmentViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.record != null) DevelopmentDialog(state, { vm.approve(it, active) }, { vm.retry(active) }, close)
    else ModalStatusDialog("Develop this idea", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
