package app.planner.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import app.planner.core.model.*

@Dao
interface PlannerDao {
    @Upsert suspend fun note(note: RawNote)
    @Upsert suspend fun record(record: Record)
    @Upsert suspend fun operation(operation: OutboxOperation)
    @Upsert suspend fun history(history: InterpretationHistory)
    @Upsert suspend fun correction(correction: UserCorrection)
    @Upsert suspend fun type(type: TypeDefinition)
    @Upsert suspend fun rule(rule: UserRule)
    @Upsert suspend fun slot(slot: IdeaSlot)
    @Upsert suspend fun meta(meta: Meta)
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun noteById(id: String): RawNote?
    @Query("SELECT * FROM records WHERE id = :id") suspend fun recordById(id: String): Record?
    @Query("SELECT * FROM meta WHERE `key` = :key") suspend fun metadata(key: String): Meta?
    @Query("SELECT * FROM meta WHERE `key` IN ('owner','cursor','network','ai','lastSync','lastCaptureMs') OR `key` IN (SELECT 'triage:' || id FROM notes WHERE processingStatus = 'review' ORDER BY createdAt LIMIT 5)") fun observeMeta(): Flow<List<Meta>>
    @Query("SELECT * FROM meta WHERE `key` = :key") fun observeMetaKey(key: String): Flow<Meta?>
    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id DESC LIMIT :limit OFFSET :offset") fun journal(limit: Int, offset: Int = 0): Flow<List<RawNote>>
    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id DESC LIMIT :limit OFFSET :offset") suspend fun notePage(limit: Int, offset: Int): List<RawNote>
    @Query("SELECT * FROM notes WHERE :before IS NULL OR createdAt < :before OR (createdAt = :before AND id < :beforeId) ORDER BY createdAt DESC, id DESC LIMIT :limit") suspend fun exportPage(limit: Int, before: String?, beforeId: String?): List<RawNote>
    @Query("SELECT * FROM notes WHERE processingStatus = 'review' ORDER BY createdAt LIMIT 5") fun triage(): Flow<List<RawNote>>
    @Query("SELECT * FROM records WHERE sourceNoteId = :id ORDER BY createdAt, id") suspend fun recordsForNote(id: String): List<Record>
    @Query("SELECT * FROM records WHERE sourceNoteId = :id ORDER BY createdAt, id") fun observeRecordsForNote(id: String): Flow<List<Record>>
    @Query("SELECT * FROM records WHERE state = 'pending' AND ((startAt IS NOT NULL AND startAt < :end AND (startAt >= :start OR endAt > :start)) OR (baseKind = 'task' AND (plannedFor = :day OR dueDate = :day OR (dueAt >= :start AND dueAt < :end)))) ORDER BY startAt, pinned DESC, createdAt, id LIMIT 200")
    fun day(day: String, start: String, end: String): Flow<List<Record>>
    @Query("SELECT * FROM records WHERE baseKind = 'task' AND state = 'completed' AND completedAt >= :start AND completedAt < :end ORDER BY completedAt DESC") fun completed(start: String, end: String): Flow<List<Record>>
    @Query("SELECT * FROM records WHERE baseKind = 'idea' AND state = 'pending' ORDER BY createdAt, id LIMIT :limit") fun ideas(limit: Int = 200): Flow<List<Record>>
    @Query("SELECT * FROM records WHERE baseKind = 'idea' AND state = 'pending' AND (deferredUntil IS NULL OR deferredUntil <= :day) ORDER BY createdAt, id LIMIT 1") suspend fun ideaCandidate(day: String): Record?
    @Query("SELECT * FROM idea_slots WHERE day = :day") suspend fun ideaSlot(day: String): IdeaSlot?
    @Query("SELECT * FROM outbox AS pending_op WHERE status = 'queued' AND nextAttemptAt <= :now AND (path = 'notes' OR NOT EXISTS (SELECT 1 FROM outbox AS parent WHERE parent.noteId = pending_op.noteId AND parent.path = 'notes')) AND NOT EXISTS (SELECT 1 FROM outbox AS earlier WHERE earlier.entityId = pending_op.entityId AND earlier.createdAt < pending_op.createdAt) ORDER BY createdAt, id LIMIT 1") suspend fun nextOperation(now: Long): OutboxOperation?
    @Query("SELECT MAX(createdAt) FROM outbox") suspend fun lastOperationTime(): Long?
    @Query("SELECT COUNT(*) FROM outbox WHERE noteId = :id") suspend fun pendingForNote(id: String): Int
    @Query("SELECT COUNT(*) FROM records WHERE sourceNoteId = :id AND isUserConfirmed = 1") suspend fun confirmedForNote(id: String): Int
    @Query("SELECT COUNT(*) FROM notes") suspend fun noteCount(): Int
    @Query("SELECT * FROM outbox WHERE entityId = :id ORDER BY createdAt") suspend fun operationsForEntity(id: String): List<OutboxOperation>
    @Query("SELECT * FROM outbox WHERE entityId = :id LIMIT 1") suspend fun pendingOperation(id: String): OutboxOperation?
    @Query("SELECT * FROM outbox ORDER BY createdAt LIMIT 100") fun observeOutbox(): Flow<List<OutboxOperation>>
    @Query("SELECT processingStatus AS status, COUNT(*) AS count FROM notes GROUP BY processingStatus") fun processingCounts(): Flow<List<StatusCount>>
    @Query("DELETE FROM outbox WHERE id = :id") suspend fun deleteOperation(id: String)
    @Query("SELECT * FROM interpretations WHERE noteId = :id ORDER BY createdAt DESC LIMIT 1") suspend fun latestHistory(id: String): InterpretationHistory?
    @Query("SELECT * FROM types ORDER BY name, schemaVersion DESC") fun types(): Flow<List<TypeDefinition>>
    @Query("SELECT * FROM types WHERE id = :id AND schemaVersion = :version") suspend fun typeById(id: String, version: Int): TypeDefinition?
    @Query("UPDATE notes SET processingStatus = :status WHERE id = :id") suspend fun noteStatus(id: String, status: String)
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM outbox") suspend fun clearOutbox()
    @Query("DELETE FROM interpretations") suspend fun clearHistories()
    @Query("DELETE FROM corrections") suspend fun clearCorrections()
    @Query("DELETE FROM idea_slots") suspend fun clearSlots()
    @Query("DELETE FROM types") suspend fun clearTypes()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM meta") suspend fun clearMeta()
}

@Database(entities = [RawNote::class, Record::class, OutboxOperation::class, InterpretationHistory::class, UserCorrection::class, TypeDefinition::class, UserRule::class, IdeaSlot::class, Meta::class], version = 3, exportSchema = true)
abstract class PlannerDatabase : RoomDatabase() { abstract fun dao(): PlannerDao }
