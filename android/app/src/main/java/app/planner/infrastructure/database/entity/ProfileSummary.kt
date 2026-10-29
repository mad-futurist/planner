package app.planner.infrastructure.database.entity

import androidx.room.*

data class ProfileSummary(val notes: Int = 0, val completed: Int = 0, val ideas: Int = 0, val tasks: Int = 0)
