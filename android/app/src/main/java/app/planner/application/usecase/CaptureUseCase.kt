package app.planner.application.usecase

import app.planner.application.port.CaptureStore
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.model.RawNote
import app.planner.domain.policy.CaptureContext
import app.planner.domain.policy.CaptureNote
import app.planner.domain.policy.LocalCapturePlan
import javax.inject.Inject

/** One capture boundary for Compose and the native floating service. */
class CaptureUseCase @Inject constructor(private val store: CaptureStore) {
    suspend fun capture(text: String, private: Boolean, source: String = "APP", manualKind: String? = null, plannedFor: String? = null, interpretationHintsJson: String = "{}", eventStartAt: String? = null, rawOnly: Boolean = false, dueDate: String? = null): RawNote =
        store.saveCapture(CaptureNote.plan(text, private, source, manualKind, plannedFor, interpretationHintsJson = interpretationHintsJson, eventStartAt = eventStartAt, rawOnly = rawOnly, dueDate = dueDate))

    suspend fun local(text: String, preview: CapturePreview, context: CaptureContext, hints: String): LocalCapturePlan =
        store.saveLocal(CaptureNote.planLocal(text, preview, context, hints), preview)
}
