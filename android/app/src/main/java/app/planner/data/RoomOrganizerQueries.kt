package app.planner.data

import app.planner.application.*
import app.planner.domain.*
import app.planner.domain.model.*
import java.time.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class RoomOrganizerQueries @Inject constructor(private val repository: PlannerRepository) : OrganizerQueries {
    private val dao get() = repository.dao
    private fun bounds(date: LocalDate) = dayBounds(date, ZoneId.systemDefault())
    override fun journal(page: Int) = dao.journal(50, page.coerceAtLeast(0) * 50)
    override fun day(date: LocalDate) = bounds(date).let { dao.day(it.day, it.start, it.end) }
    override fun dayCount(date: LocalDate) = bounds(date).let { dao.dayCount(it.day, it.start, it.end) }
    override fun completed(date: LocalDate) = bounds(date).let { dao.completed(it.start, it.end) }
    override fun ideas(query: String, page: Int) = dao.ideas(50, page.coerceAtLeast(0) * 50, query)
    override fun tasks(filter: String, date: LocalDate, page: Int) = dao.tasks(filter, date.toString(), bounds(date).end, offset = page.coerceAtLeast(0) * 50)
    override fun taskCounts(date: LocalDate) = dao.taskCounts(date.toString(), bounds(date).end).map { rows -> rows.associate { it.status to it.count } }
    override fun profile() = dao.profile()
    override fun collections() = dao.collections()
    override fun review(date: LocalDate) = bounds(date).let { bounds -> dao.reviewCandidates(date.toString(), bounds.start, isoInstant(date.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant())).map { repository.reviewTasks(date) } }
    override fun triage() = dao.triage()
    override fun types() = dao.types()
    override fun metadata() = dao.observeMeta().map { rows -> rows.associate { it.key to it.value } }
    override fun outbox() = dao.observeOutbox()
    override fun processing() = dao.processingCounts().map { rows -> rows.associate { it.status to it.count } }
    override fun records(noteId: String) = dao.observeRecordsForNote(noteId)
    override fun job(recordId: String) = dao.observeMetaKey("job:$recordId").map { it?.value }
    override suspend fun record(id: String) = dao.recordById(id)
    override suspend fun note(id: String) = dao.noteById(id)
    override suspend fun history(noteId: String) = dao.latestHistory(noteId)
    override suspend fun selectedIdea(date: LocalDate) = repository.selectedIdea(date)
}

class JsonInterpretationCodec @Inject constructor(private val gson: com.google.gson.Gson) : InterpretationCodec {
    override fun encode(hints: InterpretationHints): String = gson.toJson(hints)
    override fun decode(value: String): InterpretationHints? = runCatching { gson.fromJson(value, InterpretationHints::class.java) }.getOrNull()
}
