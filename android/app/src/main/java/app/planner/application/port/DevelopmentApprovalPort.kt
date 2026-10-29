package app.planner.application.port

import app.planner.application.model.DevelopmentSnapshot
import app.planner.domain.model.Record

interface DevelopmentWriter {
    suspend fun create(step: String, originId: String, jobId: String)
    suspend fun defer(record: Record)
    suspend fun markApplied(jobId: String)
}

interface DevelopmentApprovalPort {
    suspend fun transaction(recordId: String, jobId: String, apply: suspend (DevelopmentSnapshot, DevelopmentWriter) -> Unit)
}
