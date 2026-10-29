package app.planner.infrastructure.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fixed millisecond UTC text is essential for indexed lexical time queries.
        for ((table, columns) in mapOf("notes" to listOf("createdAt", "updatedAt"), "records" to listOf("createdAt", "updatedAt", "startAt", "endAt", "dueAt", "remindAt", "completedAt"), "interpretations" to listOf("createdAt"), "corrections" to listOf("createdAt"))) {
            for (column in columns) db.execSQL("UPDATE `$table` SET `$column` = strftime('%Y-%m-%dT%H:%M:%fZ', `$column`) WHERE `$column` IS NOT NULL")
        }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_createdAt ON outbox(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_entityId_createdAt ON outbox(entityId, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_outbox_noteId_path ON outbox(noteId, path)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN interpretationHintsJson TEXT NOT NULL DEFAULT '{}'")
        // Replay history once to recover changes discarded by pre-inbox clients.
        db.execSQL("UPDATE meta SET value = '0' WHERE `key` = 'cursor'")
    }
}
