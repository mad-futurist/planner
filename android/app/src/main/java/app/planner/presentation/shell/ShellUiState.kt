package app.planner.presentation.shell

import app.planner.presentation.common.FeedbackMessage
import app.planner.presentation.common.WorkspaceSelectionState

data class ShellUiState(val settings: Map<String, String> = emptyMap(), val metadata: Map<String, String> = emptyMap(), val processing: Map<String, Int> = emptyMap(), val message: FeedbackMessage? = null, val selection: WorkspaceSelectionState = WorkspaceSelectionState())
