package app.planner.application.port

import app.planner.domain.model.OutboxOperation

interface LocalDataGateway {
    suspend fun export(append: (String) -> Unit)
    suspend fun clear()
    suspend fun retry(operation: OutboxOperation)
}
