package app.planner.core

import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

private val instantFormatter = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
fun isoInstant(value: Instant = Instant.now()): String = instantFormatter.format(value)
fun normalizeInstant(value: String?): String? = value?.let { isoInstant(Instant.parse(it)) }
