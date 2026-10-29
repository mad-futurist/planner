package app.planner.presentation.ideas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun IdeasRoute(nav: ScreenNavigation, status: String, edit: (String)->Unit, develop: (String)->Unit, vm: IdeasViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    IdeasScreen(state, IdeasActions(vm::convert, vm::search, vm::page, vm::reloadLocalQueries), nav, status, edit, develop)
}
