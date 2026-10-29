package app.planner.infrastructure.sync

import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint @InstallIn(SingletonComponent::class)
interface SyncDependencies { fun engine(): SyncEngine; fun repository(): app.planner.infrastructure.repository.PlannerRepository; fun preferences(): app.planner.infrastructure.preferences.Preferences }
