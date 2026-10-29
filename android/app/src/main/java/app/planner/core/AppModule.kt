package app.planner.core

import android.content.Context
import androidx.room.Room
import app.planner.core.database.PlannerDatabase
import app.planner.core.database.MIGRATION_1_2
import app.planner.core.database.MIGRATION_2_3
import app.planner.core.database.MIGRATION_3_4
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides fun syncRequest(@ApplicationContext context: Context): app.planner.application.SyncRequest =
        app.planner.application.SyncRequest { app.planner.sync.SyncScheduler.request(context) }
    @Provides @Singleton fun captureInterpreter(@ApplicationContext context: Context, gson: Gson): app.planner.domain.CaptureInterpreter =
        app.planner.domain.CaptureInterpreter(context.assets.open("capture_rules_v1.json").bufferedReader().use { gson.fromJson(it, app.planner.domain.CaptureRules::class.java) })
    @Provides @Singleton fun database(@ApplicationContext context: Context): PlannerDatabase = Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    @Provides @Singleton fun gson(): Gson = Gson()
    @Provides fun queries(repository: app.planner.data.RoomOrganizerQueries): app.planner.application.OrganizerQueries = repository
    @Provides fun commands(repository: app.planner.data.PlannerRepository): app.planner.application.OrganizerCommands = repository
    @Provides fun captures(repository: app.planner.data.PlannerRepository): app.planner.application.CaptureStore = repository
    @Provides fun preferences(adapter: Preferences): app.planner.application.UserPreferences = adapter
    @Provides fun codec(adapter: app.planner.data.JsonInterpretationCodec): app.planner.application.InterpretationCodec = adapter
    @Provides fun cloud(adapter: app.planner.data.HttpCloudGateway): app.planner.application.CloudGateway = adapter
    @Provides fun localData(adapter: app.planner.data.RoomLocalDataGateway): app.planner.application.LocalDataGateway = adapter
    @Provides fun approval(adapter: app.planner.data.RoomDevelopmentApproval): app.planner.application.DevelopmentApprovalPort = adapter
}
