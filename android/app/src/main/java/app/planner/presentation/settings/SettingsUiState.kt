package app.planner.presentation.settings

import app.planner.domain.model.OutboxOperation

data class SettingsOperationState(val accountFeedback: String? = null, val browserUrl: String? = null, val contribution: Boolean? = null, val contributionError: String? = null, val clearVersion: Int = 0)

data class SettingsUiState(val settings: Map<String, String> = emptyMap(), val metadata: Map<String, String> = emptyMap(), val outbox: List<OutboxOperation> = emptyList(), val operations: SettingsOperationState = SettingsOperationState(), val busy: Set<String> = emptySet())
