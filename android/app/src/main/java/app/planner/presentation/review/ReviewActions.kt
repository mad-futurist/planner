package app.planner.presentation.review

import app.planner.domain.model.Record

data class ReviewActions(val plan: (Record) -> Unit, val snooze: (Record) -> Unit, val archive: (Record) -> Unit, val postpone: (String) -> Unit, val split: (Record?) -> Unit, val createAction: (String) -> Unit, val retry: () -> Unit)
