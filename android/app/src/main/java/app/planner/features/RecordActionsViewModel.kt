package app.planner.features

import androidx.lifecycle.*
import app.planner.application.*
import app.planner.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

abstract class RecordActionsViewModel(
    private val commands: OrganizerCommands,
    protected val selection: WorkspaceSelection,
    private val preferences: UserPreferences,
    feedback: UiFeedback,
) : FeatureViewModel(feedback) {
    fun complete(record: Record) = execute("complete:${record.id}") {
        val undo = record.state == "completed"
        commands.complete(record, undo)
        feedback.show(if (undo) "Task restored" else "Marked done", record.id.takeUnless { undo })
    }
    fun later(record: Record) = execute("later:${record.id}") { commands.later(record, selection.state.value.today) }
    fun convert(record: Record) = execute("convert:${record.id}") {
        commands.linkedTask(record, private = app.planner.core.CapturePrivacy.isPrivate(preferences.values.first()))
        feedback.show("Task created")
    }
    fun selectDate(date: LocalDate) = selection.selectDate(date)
}
