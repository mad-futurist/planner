package app.planner.infrastructure.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun request(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("capture-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun install(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("periodic-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build())
    }
}
