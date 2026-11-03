package com.aarya.kivya

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads and runs the small Qwen3 model locally. The model is not bundled in the APK. */
class KivyaOfflineChat(private val context: Context) {
    companion object {
        private const val MODEL_NAME = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
        private const val MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/$MODEL_NAME"
        private const val MIN_MODEL_BYTES = 300L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 400L * 1024L * 1024L
    }

    private val modelFile = File(context.filesDir, MODEL_NAME)
    private val mutex = Mutex()
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    fun isModelDownloaded(): Boolean =
        modelFile.exists() && modelFile.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES

    fun isReady(): Boolean = engine != null && conversation != null

    suspend fun installAndLoad(onProgress: (Int) -> Unit) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!isModelDownloaded()) downloadModel(onProgress)
            if (!isReady()) {
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = context.cacheDir.absolutePath
                    )
                )
                try {
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are KIVYA, a concise and helpful Hindi-English assistant. Reply in the user's language. Keep answers short and clear."
                            ),
                            samplerConfig = SamplerConfig(topK = 20, topP = 0.85, temperature = 0.7),
                            maxOutputToken = 100
                        )
                    )
                    engine = newEngine
                    conversation = newConversation
                } catch (error: Exception) {
                    try { newEngine.close() } catch (_: Exception) {}
                    throw error
                }
            }
        }
    }

    private fun downloadModel(onProgress: (Int) -> Unit) {
        val temporary = File(context.filesDir, "$MODEL_NAME.download")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KIVYA-Android")
        }
        try {
            connection.connect()
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Model server ने HTTP $status दिया")
            val expected = connection.contentLengthLong
            if (expected !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                throw IllegalStateException("मॉडल फ़ाइल का आकार अपेक्षा से अलग है ($expected bytes)")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        val percent = ((total * 100) / expected).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    output.flush()
                    if (total != expected || total !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                        throw IllegalStateException("डाउनलोड अधूरा है। फिर कोशिश करें।")
                    }
                }
            }
            if (modelFile.exists()) modelFile.delete()
            if (!temporary.renameTo(modelFile)) throw IllegalStateException("मॉडल फ़ाइल सेव नहीं हो पाई")
            onProgress(100)
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    suspend fun reply(prompt: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val activeConversation = conversation
                ?: throw IllegalStateException("पहले Download & Install दबाकर Offline AI तैयार करें।")
            val answer = StringBuilder()
            activeConversation.sendMessageAsync(prompt).collect { chunk -> answer.append(chunk.toString()) }
            answer.toString().trim().ifBlank { "Offline AI से खाली जवाब मिला।" }
        }
    }

    fun close() {
        try { conversation?.close() } catch (_: Exception) {}
        conversation = null
        try { engine?.close() } catch (_: Exception) {}
        engine = null
    }
}
