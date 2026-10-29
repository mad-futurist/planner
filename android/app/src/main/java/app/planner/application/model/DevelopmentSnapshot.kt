package app.planner.application.model

import app.planner.domain.model.Record

data class DevelopmentSnapshot(val record: Record, val private: Boolean, val jobId: String, val status: String, val recordVersion: Int, val steps: List<String>, val applied: Boolean)
