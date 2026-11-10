package com.aarya.kivya

import android.content.Context

/**
 * Small, app-private settings for KIVYA. No account or cloud sync is used.
 */
class KivyaSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "kivya_settings",
        Context.MODE_PRIVATE
    )

    var speechLanguage: String
        get() = prefs.getString(KEY_SPEECH_LANGUAGE, LANGUAGE_HINDI) ?: LANGUAGE_HINDI
        set(value) {
            prefs.edit().putString(
                KEY_SPEECH_LANGUAGE,
                if (value == LANGUAGE_ENGLISH) LANGUAGE_ENGLISH else LANGUAGE_HINDI
            ).apply()
        }

    var offlineModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        set(value) { prefs.edit().putBoolean(KEY_OFFLINE_MODE, value).apply() }

    var memoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_MEMORY_ENABLED, value).apply() }

    companion object {
        const val LANGUAGE_HINDI = "hi-IN"
        const val LANGUAGE_ENGLISH = "en-IN"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_OFFLINE_MODE = "offline_mode_enabled"
        private const val KEY_MEMORY_ENABLED = "memory_enabled"
    }
}
