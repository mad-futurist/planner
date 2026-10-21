package app.planner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import app.planner.data.PlannerRepository
import app.planner.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class PlannerApp : Application() {
    @Inject lateinit var repository: PlannerRepository
    override fun onCreate() {
        super.onCreate()
        repository.requestSync = { SyncScheduler.request(this) }
        SyncScheduler.install(this)
    }
}
