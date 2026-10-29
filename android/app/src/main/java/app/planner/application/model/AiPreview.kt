package app.planner.application.model

/** Remote capabilities. Android, credentials and HTTP JSON stay in the adapter. */
data class AiPreviewRecord(val kind: String, val title: String)

data class AiPreview(val records: List<AiPreviewRecord>, val needsReview: Boolean)
