package app.planner.presentation.development

import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.planner.presentation.common.ModalStatusDialog

@Composable internal fun DevelopmentRoute(id: String, close: ()->Unit, vm: DevelopmentViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.record != null) DevelopmentDialog(state, { vm.approve(it, active) }, { vm.retry(active) }, close)
    else ModalStatusDialog("Develop this idea", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
