package app.planner.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint @InstallIn(SingletonComponent::class)
interface SyncDependencies { fun engine(): SyncEngine; fun repository(): app.planner.data.PlannerRepository; fun preferences(): app.planner.core.Preferences }

class SyncWorker(context: Context, params: WorkerParameters, private val engine: SyncEngine) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return if (engine.run() && runAttemptCount < 8) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun request(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("capture-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun install(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build())
    }
}
