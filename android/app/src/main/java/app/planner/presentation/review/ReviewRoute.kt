package app.planner.presentation.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun ReviewRoute(nav: ScreenNavigation, edit: (String)->Unit, review: (String)->Unit, vm: ReviewViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ReviewScreen(state, ReviewActions(vm::plan, vm::snooze, vm::archive, vm::postpone, vm::split, vm::createAction, vm::reloadLocalQueries), nav, edit, review)
}
