package app.planner.presentation.interpretation

import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.planner.presentation.common.ModalStatusDialog

@Composable internal fun InterpretationRoute(id: String, close: ()->Unit, vm: InterpretationViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.note != null) InterpretationEditor(state, { kind, title, planned -> vm.save(kind, title, planned, active) }, close)
    else ModalStatusDialog("Organize note", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
