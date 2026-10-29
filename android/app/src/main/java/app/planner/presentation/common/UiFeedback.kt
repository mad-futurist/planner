package app.planner.presentation.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** A snackbar owns its undo payload; dismissing an older message cannot clear a newer one. */
data class FeedbackMessage(val id: Long, val text: String, val undoRecordId: String? = null)

@Singleton
class UiFeedback @Inject constructor() {
    private val current = MutableStateFlow<FeedbackMessage?>(null)
    val state = current.asStateFlow()
    private var sequence = 0L
    fun show(text: String, undoRecordId: String? = null) { current.value = FeedbackMessage(++sequence, text, undoRecordId) }
    fun dismiss(id: Long) { current.update { if (it?.id == id) null else it } }
}
