package com.example.audiodeepfakedetector.data.session

object UserSession {
    var currentUsername: String? = null

    fun logout() {
        currentUsername = null
    }
}