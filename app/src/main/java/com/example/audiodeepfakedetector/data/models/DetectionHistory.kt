package com.example.audiodeepfakedetector.data.models

data class DetectionHistory(
    val id: Long,
    val fileName: String,
    val fakePercentage: Int,
    val date: String,
    val duration: Double
)