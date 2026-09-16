package com.wildwildyeast.voiceagent

import android.content.Context

/** Small persistent settings. The API key stays on the device in app-private storage. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("voice_agent", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    var confirmEverything: Boolean
        get() = prefs.getBoolean("confirm_everything", false)
        set(value) = prefs.edit().putBoolean("confirm_everything", value).apply()

    var model: String
        get() = prefs.getString("model", "claude-opus-5") ?: "claude-opus-5"
        set(value) = prefs.edit().putString("model", value.trim()).apply()
}
