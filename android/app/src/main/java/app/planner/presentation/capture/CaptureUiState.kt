package app.planner.presentation.capture

import app.planner.domain.interpretation.CaptureDoubt
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.interpretation.InterpretationHints
import app.planner.domain.interpretation.PreviewRecord

data class CaptureUiState(
    val draft: String = "", val privateCapture: Boolean = true, val captureKind: String? = null,
    val captureDeadline: String? = null, val choosingIntent: Boolean = false,
    val captureHints: InterpretationHints = InterpretationHints(), val clarification: CaptureDoubt? = null,
    val clarificationSeed: PreviewRecord? = null, val pendingSubmission: CaptureViewModel.PendingSubmission? = null,
    val aiDraftPreview: app.planner.application.model.AiPreview? = null, val aiPreviewBusy: Boolean = false,
    val aiPreviewError: String? = null, val capturePreview: CapturePreview = CapturePreview(),
    val cloudExplanation: Boolean = false, val focusCapture: Int = 0, val saving: Boolean = false,
)
