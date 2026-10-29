package app.planner.application.port

import app.planner.domain.interpretation.InterpretationHints

interface InterpretationCodec {
    fun encode(hints: InterpretationHints): String
    fun decode(value: String): InterpretationHints?
}
