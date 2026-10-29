package app.planner.application.port

import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.model.RawNote
import app.planner.domain.policy.CapturePlan
import app.planner.domain.policy.LocalCapturePlan

interface CaptureStore {
    /** Atomically persists original, records, history and eligible outbox entries. */
    suspend fun saveCapture(plan: CapturePlan): RawNote
    suspend fun saveLocal(plan: LocalCapturePlan, preview: CapturePreview): LocalCapturePlan
}
