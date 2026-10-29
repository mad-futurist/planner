package app.planner.core

fun isoInstant(value: java.time.Instant = java.time.Instant.now()) = app.planner.domain.isoInstant(value)
fun normalizeInstant(value: String?) = app.planner.domain.normalizeInstant(value)
