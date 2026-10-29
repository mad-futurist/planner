package app.planner.presentation.settings

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.presentation.capture.CaptureViewModel
import app.planner.presentation.navigation.ScreenNavigation
import app.planner.presentation.settings.platform.OverlaySettingsRoute

@Composable internal fun SettingsRoute(nav: ScreenNavigation, capture: CaptureViewModel, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val captureState by capture.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions = remember(vm) { SettingsActions(vm::setPreference, vm::connect, vm::sync, vm::export, vm::clearLocalData, vm::retry, vm::keepLocal, vm::report, vm::account, vm::google, vm::signOut, vm::deleteAccount, vm::loadContribution, vm::contribution) }
    LaunchedEffect(state.operations.browserUrl) { state.operations.browserUrl?.let { url ->
        try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { vm.report("No browser is available") }
        finally { vm.browserOpened() }
    } }
    LaunchedEffect(state.operations.clearVersion) { if (state.operations.clearVersion > 0) { capture.selectCaptureKind(null); capture.setDraft("") } }
    SettingsScreen(state, actions, captureState.privateCapture, capture::setPrivacy, nav, { OverlaySettingsRoute(state.settings, actions.setPreference, actions.report) })
}
