package app.planner.core

import android.content.Context
import androidx.room.Room
import app.planner.core.database.PlannerDatabase
import app.planner.core.database.MIGRATION_1_2
import app.planner.core.database.MIGRATION_2_3
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): PlannerDatabase = Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    @Provides @Singleton fun gson(): Gson = Gson()
}
