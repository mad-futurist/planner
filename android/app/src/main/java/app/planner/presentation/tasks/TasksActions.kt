package app.planner.presentation.tasks

import app.planner.domain.model.Record

data class TasksActions(val complete: (Record) -> Unit, val filterTasks: (String) -> Unit, val page: (Int) -> Unit, val retry: () -> Unit)
