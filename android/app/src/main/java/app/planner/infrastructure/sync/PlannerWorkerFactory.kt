package app.planner.infrastructure.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Singleton

/** Platform composition boundary; workers receive dependencies through their constructor. */
@Singleton
class PlannerWorkerFactory @Inject constructor(private val engine: SyncEngine) : WorkerFactory() {
    override fun createWorker(context: Context, workerClassName: String, parameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name || workerClassName == "app.planner.sync.SyncWorker") SyncWorker(context, parameters, engine) else null
}
