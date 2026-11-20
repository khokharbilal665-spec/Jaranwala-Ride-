package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CompletedRideEntity::class, WeddingConvoyHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JaranwalaRoomDatabase : RoomDatabase() {

    abstract fun historyDao(): JaranwalaHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: JaranwalaRoomDatabase? = null

        fun getInstance(context: Context): JaranwalaRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JaranwalaRoomDatabase::class.java,
                    "jaranwala_ride_local.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
