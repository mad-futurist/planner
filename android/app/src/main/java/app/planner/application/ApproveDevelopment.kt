package app.planner.application

import app.planner.domain.model.Record
import javax.inject.Inject

data class DevelopmentSnapshot(val record: Record, val private: Boolean, val jobId: String, val status: String, val recordVersion: Int, val steps: List<String>, val applied: Boolean)
interface DevelopmentWriter {
    suspend fun create(step: String, originId: String, jobId: String)
    suspend fun defer(record: Record)
    suspend fun markApplied(jobId: String)
}
interface DevelopmentApprovalPort {
    suspend fun transaction(recordId: String, jobId: String, apply: suspend (DevelopmentSnapshot, DevelopmentWriter) -> Unit)
}
/** Version/idempotency policy is application logic; the adapter holds one local transaction. */
class ApproveDevelopment @Inject constructor(private val port: DevelopmentApprovalPort) {
    suspend fun approve(recordId: String, jobId: String, steps: List<String>) {
        require(steps.isNotEmpty()) { "Choose at least one step." }
        port.transaction(recordId, jobId) { snapshot, writer ->
            if (snapshot.applied) return@transaction
            val current = snapshot.record
            require(current.baseKind == "idea" && current.state == "pending") { "This idea is no longer available for development." }
            require(!snapshot.private) { "Private ideas stay on this device. Add next steps manually." }
            require(snapshot.status == "complete" && snapshot.recordVersion == current.version) { "The idea changed. Develop it again before approving." }
            require(snapshot.jobId == jobId && steps.all { it in snapshot.steps }) { "These suggestions changed. Review them again." }
            for (step in steps.distinct()) writer.create(step, current.id, jobId)
            writer.defer(current); writer.markApplied(jobId)
        }
    }
}
