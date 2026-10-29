package app.planner.presentation.capture.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import app.planner.domain.interpretation.CaptureDoubt
import app.planner.domain.interpretation.CaptureHint
import app.planner.domain.interpretation.CapturePreview
import java.time.*

internal class DoubtTransformation(private val doubts: List<CaptureDoubt>, private val hints: List<CaptureHint>, private val uncertain: androidx.compose.ui.graphics.Color, private val confirmed: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(buildAnnotatedString {
        append(text)
        for (doubt in doubts) if (doubt.end <= text.length) addStyle(SpanStyle(color = uncertain, background = uncertain.copy(alpha = .12f), textDecoration = TextDecoration.Underline), doubt.start, doubt.end)
        for (hint in hints) if (hint.end <= text.length) addStyle(SpanStyle(color = confirmed, textDecoration = TextDecoration.Underline), hint.start, hint.end)
    }, OffsetMapping.Identity)
}

/** Observe taps without consuming ordinary cursor/selection gestures. Chips provide an accessible fallback. */
internal fun Modifier.clarificationTaps(layout: () -> TextLayoutResult?, preview: CapturePreview, hints: List<CaptureHint>, open: (CaptureDoubt) -> Unit): Modifier = pointerInput(preview, hints) {
    awaitPointerEventScope {
        var down: androidx.compose.ui.geometry.Offset? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed && !change.previousPressed) down = change.position
            if (!change.pressed && change.previousPressed) {
                val origin = down
                down = null
                if (origin != null && (change.position - origin).getDistance() <= viewConfiguration.touchSlop) {
                    val position = layout()?.getOffsetForPosition(change.position) ?: continue
                    val doubt = preview.doubts.firstOrNull { position in it.start until it.end }
                        ?: hints.firstOrNull { position in it.start until it.end }?.let { CaptureDoubt(it.start, it.end, it.text, "confirmed") }
                    if (doubt != null) open(doubt)
                }
            }
        }
    }
}
