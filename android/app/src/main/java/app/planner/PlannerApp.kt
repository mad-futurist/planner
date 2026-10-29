package app.planner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import app.planner.sync.SyncScheduler
import app.planner.sync.PlannerWorkerFactory
import androidx.work.Configuration
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
