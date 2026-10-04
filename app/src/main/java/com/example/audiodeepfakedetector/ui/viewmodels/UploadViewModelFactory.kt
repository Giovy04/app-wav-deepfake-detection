package com.example.audiodeepfakedetector.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audiodeepfakedetector.ai.InferenceManager

class UploadViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadViewModel::class.java)) {
            val inferenceManager = InferenceManager.getInstance(context)
            @Suppress("UNCHECKED_CAST")
            return UploadViewModel(inferenceManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}