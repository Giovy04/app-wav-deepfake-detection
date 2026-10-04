package com.example.audiodeepfakedetector.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detections")
data class Detection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val fileName: String,
    val fakePercentage: Int,
    val timestamp: Long,
    val duration: Double
)