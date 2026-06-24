package com.mindisle.app.pet

import android.content.Context

class PetSettingManager(context: Context) {
    private val preferences = context.getSharedPreferences("mindisle_pet_visual_settings", Context.MODE_PRIVATE)

    fun saveBoolean(key: String, enabled: Boolean) {
        preferences.edit().putBoolean(key, enabled).apply()
    }

    fun readBoolean(key: String, defaultValue: Boolean = true): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    fun saveString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun readString(key: String, defaultValue: String): String {
        return preferences.getString(key, defaultValue) ?: defaultValue
    }
}
