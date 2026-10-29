package app.planner.infrastructure.sync

import android.content.Context
import androidx.work.*

class SyncWorker(context: Context, params: WorkerParameters, private val engine: SyncEngine) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return if (engine.run() && runAttemptCount < 8) Result.retry() else Result.success()
    }
}
