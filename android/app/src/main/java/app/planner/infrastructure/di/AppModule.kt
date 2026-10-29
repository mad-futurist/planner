package app.planner.infrastructure.di

import android.content.Context
import androidx.room.Room
import app.planner.infrastructure.database.MIGRATION_1_2
import app.planner.infrastructure.database.MIGRATION_2_3
import app.planner.infrastructure.database.MIGRATION_3_4
import app.planner.infrastructure.database.PlannerDatabase
import app.planner.infrastructure.preferences.Preferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides fun syncRequest(@ApplicationContext context: Context): app.planner.application.port.SyncRequest =
        app.planner.application.port.SyncRequest { app.planner.infrastructure.sync.SyncScheduler.request(context) }
    @Provides @Singleton fun captureInterpreter(@ApplicationContext context: Context, gson: Gson): app.planner.domain.interpretation.CaptureInterpreter =
        app.planner.domain.interpretation.CaptureInterpreter(context.assets.open("capture_rules_v1.json").bufferedReader().use { gson.fromJson(it, app.planner.domain.interpretation.CaptureRules::class.java) })
    @Provides @Singleton fun database(@ApplicationContext context: Context): PlannerDatabase = Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    @Provides @Singleton fun gson(): Gson = Gson()
    @Provides fun queries(repository: app.planner.infrastructure.repository.RoomOrganizerQueries): app.planner.application.port.OrganizerQueries = repository
    @Provides fun commands(repository: app.planner.infrastructure.repository.PlannerRepository): app.planner.application.port.OrganizerCommands = repository
    @Provides fun captures(repository: app.planner.infrastructure.repository.PlannerRepository): app.planner.application.port.CaptureStore = repository
    @Provides fun preferences(adapter: Preferences): app.planner.application.port.UserPreferences = adapter
    @Provides fun codec(adapter: app.planner.infrastructure.serialization.JsonInterpretationCodec): app.planner.application.port.InterpretationCodec = adapter
    @Provides fun cloud(adapter: app.planner.infrastructure.remote.HttpCloudGateway): app.planner.application.port.CloudGateway = adapter
    @Provides fun localData(adapter: app.planner.infrastructure.repository.RoomLocalDataGateway): app.planner.application.port.LocalDataGateway = adapter
    @Provides fun approval(adapter: app.planner.infrastructure.repository.RoomDevelopmentApproval): app.planner.application.port.DevelopmentApprovalPort = adapter
}
