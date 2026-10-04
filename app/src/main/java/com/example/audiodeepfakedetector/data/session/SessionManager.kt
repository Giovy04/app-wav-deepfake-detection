package com.example.audiodeepfakedetector.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SessionManager(context: Context) {
    // File XML locale privato, inaccessibile ad altre app
    private val prefs: SharedPreferences = context.getSharedPreferences("app_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "USERNAME"
    }

    fun saveLogin(username: String) {
        prefs.edit { putString(KEY_USERNAME, username) }
    }

    fun getSavedUser(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun clearSession() {
        prefs.edit { remove(KEY_USERNAME) }
    }

    fun isRemembered(): Boolean {
        return getSavedUser() != null
    }
}