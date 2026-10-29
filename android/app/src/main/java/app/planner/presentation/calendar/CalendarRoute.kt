package app.planner.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun CalendarRoute(nav: ScreenNavigation, status: String, edit: (String)->Unit, vm: CalendarViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    CalendarScreen(state, CalendarActions(vm::complete, vm::selectDate, vm::reloadLocalQueries), nav, status, edit)
}
