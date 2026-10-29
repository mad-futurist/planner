package app.planner.presentation.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun JournalRoute(nav: ScreenNavigation, edit: (String)->Unit, review: (String)->Unit, vm: JournalViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    JournalScreen(state, JournalActions(vm::reprocess, vm::page, vm::reloadLocalQueries), nav, edit, review)
}
