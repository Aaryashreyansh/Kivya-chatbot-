package com.aarya.kivya

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guest session credential storage backed by an Android Keystore AES-GCM key.
 * The access token is encrypted before it is written to private app preferences.
 */
class GuestSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("kivya_guest_secure", Context.MODE_PRIVATE)
    private val alias = "kivya_guest_token_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    @Synchronized fun read(): String? {
        val encoded = prefs.getString("token", null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = packed.first().toInt() and 0xff
            require(ivSize in 12..16 && packed.size > ivSize + 16)
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val encrypted = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized fun write(token: String) {
        require(token.length >= 40)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = byteArrayOf(iv.size.toByte()) + iv + encrypted
        check(prefs.edit().putString("token", Base64.encodeToString(packed, Base64.NO_WRAP)).commit())
    }

    @Synchronized fun clear() {
        prefs.edit().remove("token").commit()
    }
}

data class KivyaRemoteMessage(val role: String, val content: String)

class KivyaApiClient(private val store: GuestSessionStore) {
    @Volatile private var activeConversationId: String? = null

    companion object {
        private const val BASE_URL = "https://kivya-gemini-api.onrender.com"
    }

    private fun requestText(path: String, method: String = "GET", body: JSONObject? = null, auth: Boolean = true): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 45000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (auth) {
                val token = store.read() ?: throw IllegalStateException("Guest session नहीं मिला।")
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                if (status == 401 && auth) store.clear()
                val detail = try { JSONObject(raw).optString("detail") } catch (_: Exception) { "" }
                throw IllegalStateException(detail.ifBlank { "Server error ($status)" })
            }
            return raw
        } finally {
            connection.disconnect()
        }
    }

    fun ensureGuest(): String {
        store.read()?.let { return "Guest session active" }
        val response = JSONObject(requestText("/auth/guest", "POST", auth = false))
        val token = response.optString("access_token")
        store.write(token)
        return "Guest mode सक्रिय है"
    }

    fun sendMessage(message: String, conversationId: String?): Pair<String, String> {
        val body = JSONObject().put("message", message).put("language", "auto")
        val selectedId = conversationId ?: activeConversationId
        if (!selectedId.isNullOrBlank()) body.put("conversation_id", selectedId)
        val response = JSONObject(requestText("/v1/chat", "POST", body))
        activeConversationId = response.optString("conversation_id").ifBlank { null }
        return response.optString("reply") to (activeConversationId ?: "")
    }

    fun fetchLatestHistory(): List<KivyaRemoteMessage> {
        val conversations = JSONArray(requestText("/v1/conversations"))
        if (conversations.length() == 0) return emptyList()
        val id = conversations.getJSONObject(0).optString("id")
        if (id.isBlank()) return emptyList()
        activeConversationId = id
        val messages = JSONArray(requestText("/v1/conversations/$id/messages"))
        return (0 until messages.length()).mapNotNull { index ->
            val item = messages.optJSONObject(index) ?: return@mapNotNull null
            KivyaRemoteMessage(item.optString("role"), item.optString("content"))
        }
    }

    fun logout() {
        requestText("/auth/logout", "POST", JSONObject())
        store.clear()
    }

    fun deleteGuestData() {
        requestText("/auth/guest", "DELETE")
        store.clear()
    }
}
