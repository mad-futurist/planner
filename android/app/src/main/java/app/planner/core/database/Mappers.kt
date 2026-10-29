package app.planner.core.database

import app.planner.domain.model.*


fun app.planner.core.database.entities.RawNote.toDomain(): RawNote = RawNote(id = id, content = content, originalContent = originalContent, createdAt = createdAt, updatedAt = updatedAt, source = source, privacyMode = privacyMode, processingStatus = processingStatus, syncStatus = syncStatus, version = version, locale = locale, timezone = timezone, manual = manual, interpretationHintsJson = interpretationHintsJson)

fun RawNote.toStorage(): app.planner.core.database.entities.RawNote = app.planner.core.database.entities.RawNote(id = id, content = content, originalContent = originalContent, createdAt = createdAt, updatedAt = updatedAt, source = source, privacyMode = privacyMode, processingStatus = processingStatus, syncStatus = syncStatus, version = version, locale = locale, timezone = timezone, manual = manual, interpretationHintsJson = interpretationHintsJson)

fun app.planner.core.database.entities.Record.toDomain(): Record = Record(id = id, sourceNoteId = sourceNoteId, baseKind = baseKind, customTypeId = customTypeId, typeSchemaVersion = typeSchemaVersion, title = title, fieldsJson = fieldsJson, startAt = startAt, endAt = endAt, dueAt = dueAt, dueDate = dueDate, plannedFor = plannedFor, remindAt = remindAt, timezone = timezone, recurrenceRule = recurrenceRule, state = state, completedAt = completedAt, pinned = pinned, deferredUntil = deferredUntil, createdAt = createdAt, updatedAt = updatedAt, interpretationVersion = interpretationVersion, isUserConfirmed = isUserConfirmed, version = version)

fun Record.toStorage(): app.planner.core.database.entities.Record = app.planner.core.database.entities.Record(id = id, sourceNoteId = sourceNoteId, baseKind = baseKind, customTypeId = customTypeId, typeSchemaVersion = typeSchemaVersion, title = title, fieldsJson = fieldsJson, startAt = startAt, endAt = endAt, dueAt = dueAt, dueDate = dueDate, plannedFor = plannedFor, remindAt = remindAt, timezone = timezone, recurrenceRule = recurrenceRule, state = state, completedAt = completedAt, pinned = pinned, deferredUntil = deferredUntil, createdAt = createdAt, updatedAt = updatedAt, interpretationVersion = interpretationVersion, isUserConfirmed = isUserConfirmed, version = version)

fun app.planner.core.database.entities.OutboxOperation.toDomain(): OutboxOperation = OutboxOperation(id = id, entityId = entityId, noteId = noteId, path = path, method = method, payload = payload, retryCount = retryCount, nextAttemptAt = nextAttemptAt, lastError = lastError, status = status, createdAt = createdAt)

fun OutboxOperation.toStorage(): app.planner.core.database.entities.OutboxOperation = app.planner.core.database.entities.OutboxOperation(id = id, entityId = entityId, noteId = noteId, path = path, method = method, payload = payload, retryCount = retryCount, nextAttemptAt = nextAttemptAt, lastError = lastError, status = status, createdAt = createdAt)

fun app.planner.core.database.entities.InterpretationHistory.toDomain(): InterpretationHistory = InterpretationHistory(id = id, noteId = noteId, inputVersion = inputVersion, schemaVersion = schemaVersion, modelVersion = modelVersion, proposedData = proposedData, validationResult = validationResult, correctedByUser = correctedByUser, createdAt = createdAt)

fun InterpretationHistory.toStorage(): app.planner.core.database.entities.InterpretationHistory = app.planner.core.database.entities.InterpretationHistory(id = id, noteId = noteId, inputVersion = inputVersion, schemaVersion = schemaVersion, modelVersion = modelVersion, proposedData = proposedData, validationResult = validationResult, correctedByUser = correctedByUser, createdAt = createdAt)

fun app.planner.core.database.entities.UserCorrection.toDomain(): UserCorrection = UserCorrection(id = id, sourceNoteId = sourceNoteId, previousInterpretation = previousInterpretation, correctedInterpretation = correctedInterpretation, createdAt = createdAt)

fun UserCorrection.toStorage(): app.planner.core.database.entities.UserCorrection = app.planner.core.database.entities.UserCorrection(id = id, sourceNoteId = sourceNoteId, previousInterpretation = previousInterpretation, correctedInterpretation = correctedInterpretation, createdAt = createdAt)

fun app.planner.core.database.entities.TypeDefinition.toDomain(): TypeDefinition = TypeDefinition(id = id, schemaVersion = schemaVersion, name = name, baseKind = baseKind, fieldDefinitions = fieldDefinitions, displayConfiguration = displayConfiguration, allowedCapabilities = allowedCapabilities, enabled = enabled)

fun TypeDefinition.toStorage(): app.planner.core.database.entities.TypeDefinition = app.planner.core.database.entities.TypeDefinition(id = id, schemaVersion = schemaVersion, name = name, baseKind = baseKind, fieldDefinitions = fieldDefinitions, displayConfiguration = displayConfiguration, allowedCapabilities = allowedCapabilities, enabled = enabled)

fun app.planner.core.database.entities.UserRule.toDomain(): UserRule = UserRule(id = id, description = description, conditions = conditions, action = action, enabled = enabled, explicitlyApproved = explicitlyApproved)

fun UserRule.toStorage(): app.planner.core.database.entities.UserRule = app.planner.core.database.entities.UserRule(id = id, description = description, conditions = conditions, action = action, enabled = enabled, explicitlyApproved = explicitlyApproved)

fun app.planner.core.database.entities.IdeaSlot.toDomain(): IdeaSlot = IdeaSlot(day = day, recordId = recordId)

fun IdeaSlot.toStorage(): app.planner.core.database.entities.IdeaSlot = app.planner.core.database.entities.IdeaSlot(day = day, recordId = recordId)

fun app.planner.core.database.entities.Meta.toDomain(): Meta = Meta(key = key, value = value)

fun Meta.toStorage(): app.planner.core.database.entities.Meta = app.planner.core.database.entities.Meta(key = key, value = value)

fun app.planner.core.database.entities.StatusCount.toDomain(): StatusCount = StatusCount(status = status, count = count)

fun StatusCount.toStorage(): app.planner.core.database.entities.StatusCount = app.planner.core.database.entities.StatusCount(status = status, count = count)

fun app.planner.core.database.entities.ProfileSummary.toDomain(): ProfileSummary = ProfileSummary(notes = notes, completed = completed, ideas = ideas, tasks = tasks)

fun ProfileSummary.toStorage(): app.planner.core.database.entities.ProfileSummary = app.planner.core.database.entities.ProfileSummary(notes = notes, completed = completed, ideas = ideas, tasks = tasks)

fun app.planner.core.database.entities.CollectionCount.toDomain(): CollectionCount = CollectionCount(name = name, count = count)

fun CollectionCount.toStorage(): app.planner.core.database.entities.CollectionCount = app.planner.core.database.entities.CollectionCount(name = name, count = count)
