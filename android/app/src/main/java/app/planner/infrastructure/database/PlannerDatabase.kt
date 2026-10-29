package app.planner.infrastructure.database

import androidx.room.*
import app.planner.infrastructure.database.dao.PlannerDao
import app.planner.infrastructure.database.dao.StorageDao
import app.planner.infrastructure.database.entity.IdeaSlot
import app.planner.infrastructure.database.entity.InterpretationHistory
import app.planner.infrastructure.database.entity.Meta
import app.planner.infrastructure.database.entity.OutboxOperation
import app.planner.infrastructure.database.entity.RawNote
import app.planner.infrastructure.database.entity.Record
import app.planner.infrastructure.database.entity.TypeDefinition
import app.planner.infrastructure.database.entity.UserCorrection
import app.planner.infrastructure.database.entity.UserRule

@Database(entities = [RawNote::class, Record::class, OutboxOperation::class, InterpretationHistory::class, UserCorrection::class, TypeDefinition::class, UserRule::class, IdeaSlot::class, Meta::class], version = 4, exportSchema = true)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun storage(): StorageDao
    fun dao(): PlannerDao = PlannerDao(storage())
}
