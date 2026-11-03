package com.aarya.kivya

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores user-created routines locally. A routine is an ordered list of
 * allow-listed device commands; communication and financial actions are rejected.
 */
class KivyaAutomation(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "kivya_automation",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun names(): List<String> = readAll().keys.sorted()

    @Synchronized
    fun get(name: String): List<String>? =
        readAll().entries.firstOrNull { it.key.equals(name.trim(), ignoreCase = true) }?.value

    @Synchronized
    fun save(name: String, steps: List<String>): String? {
        val cleanName = name.trim().take(40)
        val cleanSteps = steps.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanName.isBlank()) return "रूटीन का नाम दें।"
        if (cleanSteps.isEmpty()) return "कम से कम एक command जोड़ें।"
        if (cleanSteps.size > MAX_STEPS) return "एक रूटीन में अधिकतम $MAX_STEPS steps रख सकते हैं।"
        val unsafe = cleanSteps.firstOrNull { !isAllowedStep(it) }
        if (unsafe != null) return "यह step अभी सुरक्षित routine command नहीं है: $unsafe"
        val all = readAll().toMutableMap()
        if (all.keys.none { it.equals(cleanName, ignoreCase = true) } && all.size >= MAX_ROUTINES) {
            return "अधिकतम $MAX_ROUTINES routines सेव हो सकती हैं।"
        }
        all.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.let { all.remove(it.key) }
        all[cleanName] = cleanSteps
        writeAll(all)
        return null
    }

    @Synchronized
    fun delete(name: String): Boolean {
        val all = readAll().toMutableMap()
        val key = all.keys.firstOrNull { it.equals(name.trim(), ignoreCase = true) } ?: return false
        all.remove(key)
        writeAll(all)
        return true
    }

    private fun isAllowedStep(command: String): Boolean {
        val c = command.lowercase()
        val blocked = listOf(
            "call", "phone ", "कॉल", "फोन करो", "message", "मैसेज", "sms",
            "whatsapp", "व्हाट्सएप", "send", "भेजो", "भेजना",
            "payment", "pay ", "पेमेंट", "पैसे", "upi", "bank", "बैंक"
        )
        if (blocked.any { c.contains(it) }) return false
        val allowed = listOf(
            "open ", "launch ", "खोलो", "ओपन", "सेटिंग", "settings",
            "wifi", "wi-fi", "वाईफाई", "bluetooth", "ब्लूटूथ",
            "camera", "कैमरा", "youtube", "यूट्यूब", "chrome", "क्रोम",
            "maps", "मैप", "नक्शा", "समय", "time", "टाइम",
            "alarm", "अलार्म", "music", "म्यूजिक", "गाना"
        )
        return allowed.any { c.contains(it) }
    }

    private fun readAll(): LinkedHashMap<String, List<String>> {
        val raw = prefs.getString(KEY_ROUTINES, null) ?: return linkedMapOf()
        return try {
            val root = JSONObject(raw)
            val result = linkedMapOf<String, List<String>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = root.optJSONArray(key) ?: continue
                val steps = buildList {
                    for (i in 0 until array.length()) {
                        val step = array.optString(i).trim()
                        if (step.isNotBlank() && isAllowedStep(step)) add(step)
                    }
                }.take(MAX_STEPS)
                if (steps.isNotEmpty()) result[key] = steps
            }
            result
        } catch (_: Exception) {
            linkedMapOf()
        }
    }

    private fun writeAll(routines: Map<String, List<String>>) {
        val root = JSONObject()
        routines.forEach { (name, steps) ->
            root.put(name, JSONArray().apply { steps.forEach { put(it) } })
        }
        prefs.edit().putString(KEY_ROUTINES, root.toString()).apply()
    }

    private companion object {
        const val KEY_ROUTINES = "saved_routines"
        const val MAX_ROUTINES = 20
        const val MAX_STEPS = 5
    }
}
