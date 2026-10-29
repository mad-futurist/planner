package app.planner.data

import app.planner.application.*
import app.planner.domain.model.*
import androidx.room.withTransaction
import com.google.gson.*
import java.time.LocalDate
import javax.inject.Inject

class RoomDevelopmentApproval @Inject constructor(private val repository: PlannerRepository) : DevelopmentApprovalPort {
    override suspend fun transaction(recordId: String, jobId: String, apply: suspend (DevelopmentSnapshot, DevelopmentWriter) -> Unit) = repository.database.withTransaction {
        val dao = repository.dao
        val current = dao.recordById(recordId) ?: error("Idea unavailable")
        val applied = dao.metadata("approved:$jobId") != null
        val job = dao.metadata("job:$recordId")?.value?.let { JsonParser.parseString(it).asJsonObject }
        val snapshot = DevelopmentSnapshot(current, dao.noteById(current.sourceNoteId)?.privacyMode == "LOCAL_ONLY", job?.get("id")?.asString.orEmpty(), job?.get("status")?.asString.orEmpty(), job?.get("recordVersion")?.asInt ?: 0, job?.get("result")?.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("steps")?.map { it.asString }.orEmpty(), applied)
        apply(snapshot, object : DevelopmentWriter {
            override suspend fun create(step: String, originId: String, jobId: String) {
                val note = repository.capture(step, false, manualKind = "task", plannedFor = LocalDate.now().toString())
                val task = dao.recordsForNote(note.id).single()
                repository.edit(task.copy(fieldsJson = JsonObject().apply { addProperty("developedFrom", originId); addProperty("suggestionJobId", jobId) }.toString()))
            }
            override suspend fun defer(record: Record) { repository.later(record, LocalDate.now()) }
            override suspend fun markApplied(jobId: String) { dao.meta(Meta("approved:$jobId", "true")) }
        })
    }
}
