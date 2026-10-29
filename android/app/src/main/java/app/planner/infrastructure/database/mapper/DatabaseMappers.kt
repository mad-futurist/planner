package app.planner.infrastructure.database.mapper

import app.planner.domain.model.CollectionCount
import app.planner.domain.model.IdeaSlot
import app.planner.domain.model.InterpretationHistory
import app.planner.domain.model.Meta
import app.planner.domain.model.OutboxOperation
import app.planner.domain.model.ProfileSummary
import app.planner.domain.model.RawNote
import app.planner.domain.model.Record
import app.planner.domain.model.StatusCount
import app.planner.domain.model.TypeDefinition
import app.planner.domain.model.UserCorrection
import app.planner.domain.model.UserRule

fun app.planner.infrastructure.database.entity.RawNote.toDomain(): RawNote = RawNote(id = id, content = content, originalContent = originalContent, createdAt = createdAt, updatedAt = updatedAt, source = source, privacyMode = privacyMode, processingStatus = processingStatus, syncStatus = syncStatus, version = version, locale = locale, timezone = timezone, manual = manual, interpretationHintsJson = interpretationHintsJson)

fun RawNote.toStorage(): app.planner.infrastructure.database.entity.RawNote = app.planner.infrastructure.database.entity.RawNote(id = id, content = content, originalContent = originalContent, createdAt = createdAt, updatedAt = updatedAt, source = source, privacyMode = privacyMode, processingStatus = processingStatus, syncStatus = syncStatus, version = version, locale = locale, timezone = timezone, manual = manual, interpretationHintsJson = interpretationHintsJson)

fun app.planner.infrastructure.database.entity.Record.toDomain(): Record = Record(id = id, sourceNoteId = sourceNoteId, baseKind = baseKind, customTypeId = customTypeId, typeSchemaVersion = typeSchemaVersion, title = title, fieldsJson = fieldsJson, startAt = startAt, endAt = endAt, dueAt = dueAt, dueDate = dueDate, plannedFor = plannedFor, remindAt = remindAt, timezone = timezone, recurrenceRule = recurrenceRule, state = state, completedAt = completedAt, pinned = pinned, deferredUntil = deferredUntil, createdAt = createdAt, updatedAt = updatedAt, interpretationVersion = interpretationVersion, isUserConfirmed = isUserConfirmed, version = version)

fun Record.toStorage(): app.planner.infrastructure.database.entity.Record = app.planner.infrastructure.database.entity.Record(id = id, sourceNoteId = sourceNoteId, baseKind = baseKind, customTypeId = customTypeId, typeSchemaVersion = typeSchemaVersion, title = title, fieldsJson = fieldsJson, startAt = startAt, endAt = endAt, dueAt = dueAt, dueDate = dueDate, plannedFor = plannedFor, remindAt = remindAt, timezone = timezone, recurrenceRule = recurrenceRule, state = state, completedAt = completedAt, pinned = pinned, deferredUntil = deferredUntil, createdAt = createdAt, updatedAt = updatedAt, interpretationVersion = interpretationVersion, isUserConfirmed = isUserConfirmed, version = version)

fun app.planner.infrastructure.database.entity.OutboxOperation.toDomain(): OutboxOperation = OutboxOperation(id = id, entityId = entityId, noteId = noteId, path = path, method = method, payload = payload, retryCount = retryCount, nextAttemptAt = nextAttemptAt, lastError = lastError, status = status, createdAt = createdAt)

fun OutboxOperation.toStorage(): app.planner.infrastructure.database.entity.OutboxOperation = app.planner.infrastructure.database.entity.OutboxOperation(id = id, entityId = entityId, noteId = noteId, path = path, method = method, payload = payload, retryCount = retryCount, nextAttemptAt = nextAttemptAt, lastError = lastError, status = status, createdAt = createdAt)

fun app.planner.infrastructure.database.entity.InterpretationHistory.toDomain(): InterpretationHistory = InterpretationHistory(id = id, noteId = noteId, inputVersion = inputVersion, schemaVersion = schemaVersion, modelVersion = modelVersion, proposedData = proposedData, validationResult = validationResult, correctedByUser = correctedByUser, createdAt = createdAt)

fun InterpretationHistory.toStorage(): app.planner.infrastructure.database.entity.InterpretationHistory = app.planner.infrastructure.database.entity.InterpretationHistory(id = id, noteId = noteId, inputVersion = inputVersion, schemaVersion = schemaVersion, modelVersion = modelVersion, proposedData = proposedData, validationResult = validationResult, correctedByUser = correctedByUser, createdAt = createdAt)

fun app.planner.infrastructure.database.entity.UserCorrection.toDomain(): UserCorrection = UserCorrection(id = id, sourceNoteId = sourceNoteId, previousInterpretation = previousInterpretation, correctedInterpretation = correctedInterpretation, createdAt = createdAt)

fun UserCorrection.toStorage(): app.planner.infrastructure.database.entity.UserCorrection = app.planner.infrastructure.database.entity.UserCorrection(id = id, sourceNoteId = sourceNoteId, previousInterpretation = previousInterpretation, correctedInterpretation = correctedInterpretation, createdAt = createdAt)

fun app.planner.infrastructure.database.entity.TypeDefinition.toDomain(): TypeDefinition = TypeDefinition(id = id, schemaVersion = schemaVersion, name = name, baseKind = baseKind, fieldDefinitions = fieldDefinitions, displayConfiguration = displayConfiguration, allowedCapabilities = allowedCapabilities, enabled = enabled)

fun TypeDefinition.toStorage(): app.planner.infrastructure.database.entity.TypeDefinition = app.planner.infrastructure.database.entity.TypeDefinition(id = id, schemaVersion = schemaVersion, name = name, baseKind = baseKind, fieldDefinitions = fieldDefinitions, displayConfiguration = displayConfiguration, allowedCapabilities = allowedCapabilities, enabled = enabled)

fun app.planner.infrastructure.database.entity.UserRule.toDomain(): UserRule = UserRule(id = id, description = description, conditions = conditions, action = action, enabled = enabled, explicitlyApproved = explicitlyApproved)

fun UserRule.toStorage(): app.planner.infrastructure.database.entity.UserRule = app.planner.infrastructure.database.entity.UserRule(id = id, description = description, conditions = conditions, action = action, enabled = enabled, explicitlyApproved = explicitlyApproved)

fun app.planner.infrastructure.database.entity.IdeaSlot.toDomain(): IdeaSlot = IdeaSlot(day = day, recordId = recordId)

fun IdeaSlot.toStorage(): app.planner.infrastructure.database.entity.IdeaSlot = app.planner.infrastructure.database.entity.IdeaSlot(day = day, recordId = recordId)

fun app.planner.infrastructure.database.entity.Meta.toDomain(): Meta = Meta(key = key, value = value)

fun Meta.toStorage(): app.planner.infrastructure.database.entity.Meta = app.planner.infrastructure.database.entity.Meta(key = key, value = value)

fun app.planner.infrastructure.database.entity.StatusCount.toDomain(): StatusCount = StatusCount(status = status, count = count)

fun StatusCount.toStorage(): app.planner.infrastructure.database.entity.StatusCount = app.planner.infrastructure.database.entity.StatusCount(status = status, count = count)

fun app.planner.infrastructure.database.entity.ProfileSummary.toDomain(): ProfileSummary = ProfileSummary(notes = notes, completed = completed, ideas = ideas, tasks = tasks)

fun ProfileSummary.toStorage(): app.planner.infrastructure.database.entity.ProfileSummary = app.planner.infrastructure.database.entity.ProfileSummary(notes = notes, completed = completed, ideas = ideas, tasks = tasks)

fun app.planner.infrastructure.database.entity.CollectionCount.toDomain(): CollectionCount = CollectionCount(name = name, count = count)

fun CollectionCount.toStorage(): app.planner.infrastructure.database.entity.CollectionCount = app.planner.infrastructure.database.entity.CollectionCount(name = name, count = count)
