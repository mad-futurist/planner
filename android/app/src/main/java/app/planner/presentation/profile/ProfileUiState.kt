package app.planner.presentation.profile

import app.planner.domain.model.CollectionCount
import app.planner.domain.model.ProfileSummary

data class ProfileUiState(
    val profile: ProfileSummary = ProfileSummary(),
    val collections: List<CollectionCount> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val reads: Map<String, String> = emptyMap(),
)
