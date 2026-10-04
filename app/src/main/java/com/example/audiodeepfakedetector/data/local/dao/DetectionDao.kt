package com.example.audiodeepfakedetector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.audiodeepfakedetector.data.local.entities.Detection

@Dao
interface DetectionDao {
    @Insert
    suspend fun insertDetection(detection: Detection)

    @Query("SELECT * FROM detections WHERE username = :username ORDER BY timestamp DESC")
    suspend fun getDetectionsForUser(username: String): List<Detection>

    @Query("DELETE FROM detections WHERE username = :username")
    suspend fun deleteDetectionsForUser(username: String)
}
