package app.planner.presentation.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun TasksRoute(nav: ScreenNavigation, status: String, edit: (String)->Unit, addTask: ()->Unit, vm: TasksViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    TasksScreen(state, TasksActions(vm::complete, vm::filterTasks, vm::page, vm::reloadLocalQueries), nav, status, edit, addTask)
}
