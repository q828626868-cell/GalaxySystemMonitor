package com.galaxy.systemmonitor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Stored in the app's private database directory (/data/data/<package>/databases). */
@Database(
    entities = [SampleEntity::class, NetworkEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun networkEventDao(): NetworkEventDao

    companion object {
        const val NAME = "monitor.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
