package app.planner.presentation.capture

import app.planner.domain.interpretation.CaptureDoubt
import app.planner.domain.interpretation.CaptureHint
import app.planner.domain.interpretation.CapturePreview
import app.planner.domain.interpretation.InterpretationHints
import app.planner.domain.interpretation.PreviewRecord
import java.time.LocalDate

data class CaptureActions(
    val setDraft: (String) -> Unit,
    val selectKind: (String?) -> Unit,
    val save: (String?, Boolean) -> Unit,
    val privacy: (Boolean) -> Unit,
    val showClarification: (CaptureDoubt) -> Unit,
    val chooseDay: (PreviewRecord, LocalDate) -> Unit,
    val chooseIntent: (String, String?) -> Unit,
    val resetClarifications: () -> Unit,
    val createEvent: () -> Unit,
    val requestAiPreview: () -> Unit,
    val cancelClarification: () -> Unit,
    val clarify: (CaptureHint) -> Unit,
    val preview: (InterpretationHints) -> CapturePreview,
)
