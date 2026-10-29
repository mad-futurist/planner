package app.planner.infrastructure.serialization

import app.planner.application.port.InterpretationCodec
import app.planner.domain.interpretation.InterpretationHints
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.flow.*

class JsonInterpretationCodec @Inject constructor(private val gson: com.google.gson.Gson) : InterpretationCodec {
    override fun encode(hints: InterpretationHints): String = gson.toJson(hints)
    override fun decode(value: String): InterpretationHints? = runCatching { gson.fromJson(value, InterpretationHints::class.java) }.getOrNull()
}
