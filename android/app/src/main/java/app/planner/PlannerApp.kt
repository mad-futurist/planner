package app.planner

import android.app.Application
import androidx.work.Configuration
import app.planner.infrastructure.sync.PlannerWorkerFactory
import app.planner.infrastructure.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PlannerApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: PlannerWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.install(this)
    }
}
