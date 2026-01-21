package com.realwear.imagettext

import android.content.Context

object SettingsManager {
    
    private const val PREFS_NAME = "ImageTText_Settings"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SERVER_IP = "server_ip"
    private const val DEFAULT_API_KEY = "e98b288d2bmsh06ace0e29bf7d83p1ae9fajsn9f0ef84e9691"
    private const val DEFAULT_SERVER_IP = "192.168.1.65"

    fun getApiKey(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, apiKey: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getServerIP(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_SERVER_IP, DEFAULT_SERVER_IP) ?: DEFAULT_SERVER_IP
    }

    fun setServerIP(context: Context, serverIP: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_SERVER_IP, serverIP).apply()
    }
}
