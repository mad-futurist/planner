package app.planner.presentation.record

import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.planner.presentation.common.ModalStatusDialog

@Composable internal fun RecordRoute(id: String, related: (String)->Unit, close: ()->Unit, vm: RecordViewModel = hiltViewModel()) {
    var session by remember(id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(id) { session = vm.open(id) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val active = session
    LaunchedEffect(state.closeVersion, state.sessionId, active) { if (state.sessionId == active && state.closeVersion > 0) close() }
    if (active != null && state.sessionId == active && state.record != null) RecordEditor(state, { vm.save(it, active) }, { vm.complete(it, active) }, related, close)
    else ModalStatusDialog("Details", state.error.takeIf { state.sessionId == active }, { session = vm.open(id) }, close)
}
