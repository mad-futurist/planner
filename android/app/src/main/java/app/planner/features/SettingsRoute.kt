package app.planner.features

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import app.planner.navigation.ScreenNavigation
import app.planner.domain.model.OutboxOperation
import java.io.Writer

data class SettingsActions(
    val setPreference: (String, String)->Unit, val connect: (String, String)->Unit, val sync: ()->Unit,
    val export: (() -> Writer?) -> Unit, val clear: ()->Unit,
    val retry: (OutboxOperation)->Unit, val keepLocal: (OutboxOperation)->Unit, val report: (String)->Unit,
    val account: (String, String, String, String)->Unit, val google: (String, String?)->Unit,
    val signOut: ()->Unit, val deleteAccount: (String)->Unit,
    val loadContribution: ()->Unit, val contribution: (Boolean?)->Unit,
)

@Composable internal fun SettingsRoute(nav: NavHostController, capture: CaptureViewModel, vm: SettingsViewModel = hiltViewModel()) {
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
    SettingsScreen(state, actions, captureState.privateCapture, capture::setPrivacy, remember(nav) { ScreenNavigation({ nav.navigate(it) }, { nav.popBackStack() }) })
}
