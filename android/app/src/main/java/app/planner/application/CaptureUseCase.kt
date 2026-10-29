package app.planner.application

import app.planner.domain.*
import app.planner.domain.model.RawNote
import javax.inject.Inject

/** One capture boundary for Compose and the native floating service. */
class CaptureUseCase @Inject constructor(private val store: CaptureStore) {
    suspend fun capture(text: String, private: Boolean, source: String = "APP", manualKind: String? = null, plannedFor: String? = null, interpretationHintsJson: String = "{}", eventStartAt: String? = null, rawOnly: Boolean = false, dueDate: String? = null): RawNote =
        store.saveCapture(CaptureNote.plan(text, private, source, manualKind, plannedFor, interpretationHintsJson = interpretationHintsJson, eventStartAt = eventStartAt, rawOnly = rawOnly, dueDate = dueDate))

    suspend fun local(text: String, preview: CapturePreview, context: CaptureContext, hints: String): LocalCapturePlan =
        store.saveLocal(CaptureNote.planLocal(text, preview, context, hints), preview)
}
