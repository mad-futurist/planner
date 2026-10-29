package app.planner.presentation.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.planner.presentation.navigation.ScreenNavigation

@Composable
internal fun ProfileRoute(nav: ScreenNavigation, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    ProfileScreen(state, ProfileActions(vm::reloadLocalQueries), nav)
}
