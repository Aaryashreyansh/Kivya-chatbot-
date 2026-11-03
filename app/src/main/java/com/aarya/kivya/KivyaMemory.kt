package com.aarya.kivya

import android.content.Context
import org.json.JSONArray

/**
 * Small on-device conversation history.
 * Stores only the latest 30 chat lines in app-private SharedPreferences.
 */
class KivyaMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "kivya_local_memory",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val line = array.optString(index)
                    if (line.isNotBlank()) add(line)
                }
            }.takeLast(MAX_LINES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(lines: List<String>) {
        val array = JSONArray()
        lines.takeLast(MAX_LINES).forEach { line ->
            if (line.isNotBlank()) array.put(line.take(MAX_LINE_LENGTH))
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val KEY_HISTORY = "conversation"
        const val MAX_LINES = 30
        const val MAX_LINE_LENGTH = 2000
    }
}
