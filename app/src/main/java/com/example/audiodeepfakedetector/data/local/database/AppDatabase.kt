package com.example.audiodeepfakedetector.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.audiodeepfakedetector.data.local.dao.DetectionDao
import com.example.audiodeepfakedetector.data.local.dao.UserDao
import com.example.audiodeepfakedetector.data.local.entities.Detection
import com.example.audiodeepfakedetector.data.local.entities.User

@Database(entities = [User::class, Detection::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun detectionDao(): DetectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audio_detector_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
