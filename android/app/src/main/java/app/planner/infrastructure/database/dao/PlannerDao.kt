package app.planner.infrastructure.database.dao

import app.planner.domain.model.CollectionCount
import app.planner.domain.model.IdeaSlot
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.Meta
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.ProfileSummary
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.StatusCount
import app.planner.domain.model.TypeDefinition
import app.planner.domain.model.UserCorrection
import app.planner.domain.model.UserRule
import app.planner.infrastructure.database.mapper.toDomain
import app.planner.infrastructure.database.mapper.toStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Infrastructure-only mapped access. Never injected into presentation or application. */
class PlannerDao(private val storage: StorageDao) {
    suspend fun note(note: RawNote) = storage.note(note.toStorage())
    suspend fun record(record: Record) = storage.record(record.toStorage())
    suspend fun operation(operation: OutboxOperation) = storage.operation(operation.toStorage())
    suspend fun history(history: InterpretationHistory) = storage.history(history.toStorage())
    suspend fun correction(correction: UserCorrection) = storage.correction(correction.toStorage())
    suspend fun type(type: TypeDefinition) = storage.type(type.toStorage())
    suspend fun rule(rule: UserRule) = storage.rule(rule.toStorage())
    suspend fun slot(slot: IdeaSlot) = storage.slot(slot.toStorage())
    suspend fun meta(meta: Meta) = storage.meta(meta.toStorage())
    suspend fun noteById(id: String): RawNote? = storage.noteById(id)?.toDomain()
    suspend fun recordById(id: String): Record? = storage.recordById(id)?.toDomain()
    suspend fun metadata(key: String): Meta? = storage.metadata(key)?.toDomain()
    suspend fun incoming(after: String = "", limit: Int = 100): List<Meta> = storage.incoming(after, limit).map { it.toDomain() }
    suspend fun deleteMeta(key: String) = storage.deleteMeta(key)
    fun observeMeta(): Flow<List<Meta>> = storage.observeMeta().map { rows -> rows.map { it.toDomain() } }
    fun observeMetaKey(key: String): Flow<Meta?> = storage.observeMetaKey(key).map { it?.toDomain() }
    fun journal(limit: Int, offset: Int = 0): Flow<List<RawNote>> = storage.journal(limit, offset).map { rows -> rows.map { it.toDomain() } }
    suspend fun notePage(limit: Int, offset: Int): List<RawNote> = storage.notePage(limit, offset).map { it.toDomain() }
    suspend fun exportPage(limit: Int, before: String?, beforeId: String?): List<RawNote> = storage.exportPage(limit, before, beforeId).map { it.toDomain() }
    fun triage(): Flow<List<RawNote>> = storage.triage().map { rows -> rows.map { it.toDomain() } }
    suspend fun recordsForNote(id: String): List<Record> = storage.recordsForNote(id).map { it.toDomain() }
    fun observeRecordsForNote(id: String): Flow<List<Record>> = storage.observeRecordsForNote(id).map { rows -> rows.map { it.toDomain() } }
    fun day(day: String, start: String, end: String): Flow<List<Record>> = storage.day(day, start, end).map { rows -> rows.map { it.toDomain() } }
    fun dayCount(day: String, start: String, end: String): Flow<Int> = storage.dayCount(day, start, end)
    fun completed(start: String, end: String): Flow<List<Record>> = storage.completed(start, end).map { rows -> rows.map { it.toDomain() } }
    fun ideas(limit: Int = 200, offset: Int = 0, query: String = ""): Flow<List<Record>> = storage.ideas(limit, offset, query).map { rows -> rows.map { it.toDomain() } }
    suspend fun ideaCandidate(day: String): Record? = storage.ideaCandidate(day)?.toDomain()
    suspend fun ideaSlot(day: String): IdeaSlot? = storage.ideaSlot(day)?.toDomain()
    fun tasks(filter: String, day: String, end: String, limit: Int = 50, offset: Int = 0): Flow<List<Record>> = storage.tasks(filter, day, end, limit, offset).map { rows -> rows.map { it.toDomain() } }
    fun taskCounts(day: String, end: String): Flow<List<StatusCount>> = storage.taskCounts(day, end).map { rows -> rows.map { it.toDomain() } }
    fun reviewCandidates(day: String, start: String, oldBefore: String): Flow<List<Record>> = storage.reviewCandidates(day, start, oldBefore).map { rows -> rows.map { it.toDomain() } }
    fun profile(): Flow<ProfileSummary> = storage.profile().map { it.toDomain() }
    fun collections(): Flow<List<CollectionCount>> = storage.collections().map { rows -> rows.map { it.toDomain() } }
    suspend fun nextOperation(now: Long): OutboxOperation? = storage.nextOperation(now)?.toDomain()
    suspend fun lastOperationTime(): Long? = storage.lastOperationTime()
    suspend fun pendingForNote(id: String): Int = storage.pendingForNote(id)
    suspend fun confirmedForNote(id: String): Int = storage.confirmedForNote(id)
    suspend fun noteCount(): Int = storage.noteCount()
    suspend fun operationsForEntity(id: String): List<OutboxOperation> = storage.operationsForEntity(id).map { it.toDomain() }
    suspend fun pendingOperation(id: String): OutboxOperation? = storage.pendingOperation(id)?.toDomain()
    fun observeOutbox(): Flow<List<OutboxOperation>> = storage.observeOutbox().map { rows -> rows.map { it.toDomain() } }
    fun processingCounts(): Flow<List<StatusCount>> = storage.processingCounts().map { rows -> rows.map { it.toDomain() } }
    suspend fun deleteOperation(id: String) = storage.deleteOperation(id)
    suspend fun latestHistory(id: String): InterpretationHistory? = storage.latestHistory(id)?.toDomain()
    fun types(): Flow<List<TypeDefinition>> = storage.types().map { rows -> rows.map { it.toDomain() } }
    suspend fun typeById(id: String, version: Int): TypeDefinition? = storage.typeById(id, version)?.toDomain()
    suspend fun noteStatus(id: String, status: String) = storage.noteStatus(id, status)
    suspend fun clearNotes() = storage.clearNotes()
    suspend fun clearRecords() = storage.clearRecords()
    suspend fun clearOutbox() = storage.clearOutbox()
    suspend fun clearHistories() = storage.clearHistories()
    suspend fun clearCorrections() = storage.clearCorrections()
    suspend fun clearSlots() = storage.clearSlots()
    suspend fun clearTypes() = storage.clearTypes()
    suspend fun clearRules() = storage.clearRules()
    suspend fun clearMeta() = storage.clearMeta()
}
