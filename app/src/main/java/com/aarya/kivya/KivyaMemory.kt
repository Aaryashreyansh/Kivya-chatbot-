package com.aarya.kivya

import android.content.Context
import org.json.JSONArray

/**
 * Explicit, user-controlled local memory.
 * Conversation history and saved notes are stored only in app-private preferences.
 * Notes are saved only when the user explicitly asks KIVYA to remember them.
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

    @Synchronized
    fun loadNotes(): List<String> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val note = array.optString(index).trim()
                    if (note.isNotBlank()) add(note)
                }
            }.takeLast(MAX_NOTES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun rememberNote(note: String): Boolean {
        val clean = note.trim().take(MAX_NOTE_LENGTH)
        if (clean.isBlank()) return false
        val notes = loadNotes().toMutableList()
        if (notes.any { it.equals(clean, ignoreCase = true) }) return true
        notes.add(clean)
        val array = JSONArray()
        notes.takeLast(MAX_NOTES).forEach { array.put(it) }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
        return true
    }

    @Synchronized
    fun forgetNotes() {
        prefs.edit().remove(KEY_NOTES).apply()
    }

    @Synchronized
    fun forgetHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_HISTORY).remove(KEY_NOTES).apply()
    }

    private companion object {
        const val KEY_HISTORY = "conversation"
        const val KEY_NOTES = "explicit_notes"
        const val MAX_LINES = 30
        const val MAX_LINE_LENGTH = 2000
        const val MAX_NOTES = 50
        const val MAX_NOTE_LENGTH = 500
    }
}
