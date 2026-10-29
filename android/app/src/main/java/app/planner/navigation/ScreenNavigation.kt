package app.planner.navigation

/** Stateless presentation callbacks; screen content never receives a navigation controller. */
data class ScreenNavigation(val navigate: (String) -> Unit, val back: () -> Unit)
