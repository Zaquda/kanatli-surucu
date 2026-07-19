package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, ShiftEntity::class, TripEntity::class],
    version = 8,
    exportSchema = false
)
abstract class MartiDatabase : RoomDatabase() {
    abstract fun martiDao(): MartiDao

    companion object {
        @Volatile
        private var INSTANCE: MartiDatabase? = null

        fun getDatabase(context: Context): MartiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MartiDatabase::class.java,
                    "marti_tag_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
