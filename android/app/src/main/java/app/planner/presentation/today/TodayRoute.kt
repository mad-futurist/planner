package app.planner.presentation.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun TodayRoute(nav: ScreenNavigation, status: String, edit: (String)->Unit, develop: (String)->Unit, addTask: ()->Unit, cloud: ()->Unit, vm: TodayViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    TodayScreen(state, TodayActions(vm::complete, vm::later, vm::selectDate, cloud, vm::reloadLocalQueries), nav, status, edit, develop, addTask)
}
