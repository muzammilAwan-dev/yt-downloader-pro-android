package io.github.muzammilawan.clipharbor.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1 of the durable queue. `exportSchema = true` writes app/schemas/<class>.json on first build so a future
 * version bump can ship a real migration test; Room will fail the build if the schema ever diverges silently.
 *
 * No TypeConverters today: Room stores [TaskStage] and [TaskMediaMode] by name and reads them back by name; a rename
 * would be a migration hazard, so both enums are treated as fixed vocabulary.
 */
@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ytdlp.db")
                .build()
    }
}
