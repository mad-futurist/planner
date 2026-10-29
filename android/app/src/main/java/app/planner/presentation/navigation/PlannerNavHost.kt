package app.planner.presentation.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.planner.presentation.capture.CaptureViewModel
import app.planner.presentation.today.TodayRoute
import app.planner.presentation.calendar.CalendarRoute
import app.planner.presentation.tasks.TasksRoute
import app.planner.presentation.ideas.IdeasRoute
import app.planner.presentation.profile.ProfileRoute
import app.planner.presentation.journal.JournalRoute
import app.planner.presentation.review.ReviewRoute
import app.planner.presentation.settings.SettingsRoute

@Composable
internal fun PlannerNavHost(nav: NavHostController, modifier: Modifier, status: String?, edit: (String)->Unit, review: (String)->Unit, develop: (String)->Unit, addTask: (String)->Unit, capture: CaptureViewModel) {
    val navigation = remember(nav) { ScreenNavigation({ nav.navigate(it) }, { nav.popBackStack() }) }
    NavHost(nav, PlannerRoutes.Today, modifier) {
        composable(PlannerRoutes.Today) { TodayRoute(navigation, status.orEmpty(), edit, develop, { addTask("task") }, { capture.setPrivacy(false) }) }
        composable(PlannerRoutes.Calendar) { CalendarRoute(navigation, status.orEmpty(), edit) }
        composable(PlannerRoutes.Tasks) { TasksRoute(navigation, status.orEmpty(), edit, { addTask("task_unscheduled") }) }
        composable(PlannerRoutes.Ideas) { IdeasRoute(navigation, status.orEmpty(), edit, develop) }
        composable(PlannerRoutes.Profile) { ProfileRoute(navigation) }
        composable(PlannerRoutes.Journal) { JournalRoute(navigation, edit, review) }
        composable(PlannerRoutes.Review) { ReviewRoute(navigation, edit, review) }
        composable(PlannerRoutes.Settings) { SettingsRoute(navigation, capture) }
    }
}
