package com.webworkspace.app.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("workspace_settings", Context.MODE_PRIVATE)

    var defaultWorkspaceUrl: String
        get() = prefs.getString(KEY_DEFAULT_URL, "https://flow.google.com") ?: "https://flow.google.com"
        set(value) = prefs.edit().putString(KEY_DEFAULT_URL, value).apply()

    var startWithLastProfile: Boolean
        get() = prefs.getBoolean(KEY_START_LAST_PROFILE, false)
        set(value) = prefs.edit().putBoolean(KEY_START_LAST_PROFILE, value).apply()

    companion object {
        private const val KEY_DEFAULT_URL = "default_url"
        private const val KEY_START_LAST_PROFILE = "start_last_profile"
    }
}
