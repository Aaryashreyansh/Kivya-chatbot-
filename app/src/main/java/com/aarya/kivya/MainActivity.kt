package com.aarya.kivya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF090F20)
private val Card = Color(0xFF17233B)
private val Aqua = Color(0xFF68E7DE)
private val Lilac = Color(0xFF9D8CFF)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN")
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) speech = SpeechRecognizer.createSpeechRecognizer(this)
        setContent { KivyaHome(::startListening, ::say, ::phoneAction) }
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kivya-reply")
    }

    private fun startListening(onResult: (String) -> Unit, onFailure: (String) -> Unit) {
        val engine = speech ?: run { onFailure("इस फोन पर speech recognition उपलब्ध नहीं है।"); return }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onFailure("आवाज़ साफ़ नहीं मिली। फिर कोशिश करो।") }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (words.isNullOrBlank()) onFailure("कुछ सुनाई नहीं दिया।") else onResult(words)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Kivya से बात करें")
        }
        engine.startListening(intent)
    }

    private fun phoneAction(command: String): String {
        val c = command.lowercase(Locale.ROOT)
        return try {
            when {
                "सेटिंग" in c || "settings" in c -> { startActivity(Intent(Settings.ACTION_SETTINGS)); "सेटिंग्स खोल रही हूँ।" }
                "वाईफाई" in c || "wifi" in c || "wi-fi" in c -> { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); "वाई-फाई सेटिंग्स खोल रही हूँ।" }
                "ब्लूटूथ" in c || "bluetooth" in c -> { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "ब्लूटूथ सेटिंग्स खोल रही हूँ।" }
                "यूट्यूब" in c || "youtube" in c -> openWeb("https://www.youtube.com", "YouTube खोल रही हूँ।")
                "गूगल" in c || "google" in c || "सर्च" in c -> openWeb("https://www.google.com", "Google खोल रही हूँ।")
                "समय" in c || "टाइम" in c || "time" in c -> "अभी समय ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())} है।"
                "मैप" in c || "नक्शा" in c || "maps" in c -> openWeb("https://maps.google.com", "Maps खोल रही हूँ।")
                else -> "यह command अभी उपलब्ध नहीं है। तुम Settings, Wi-Fi, Bluetooth, YouTube, Google या Maps कह सकते हो।"
            }
        } catch (_: Exception) { "यह काम पूरा नहीं हो पाया।" }
    }

    private fun openWeb(url: String, message: String): String {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return message
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun KivyaHome(
    listen: ((String) -> Unit, (String) -> Unit) -> Unit,
    speak: (String) -> Unit,
    phoneAction: (String) -> String
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("Phone") }
    var prompt by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("नमस्ते! मैं Kivya हूँ। बताओ, क्या करना है?") }
    var listening by remember { mutableStateOf(false) }
    val chat = remember { mutableStateListOf("Kivya: मैं तैयार हूँ। बोलकर या लिखकर command दो।") }

    fun process(text: String) {
        prompt = text
        val answer = if (mode == "Phone") phoneAction(text) else
            "AI Tools mode तैयार है, लेकिन GitHub/AWS execution अभी backend से connect नहीं है। सुरक्षा के लिए cloud credentials APK में नहीं रखेंगे।"
        response = answer
        chat.add("तुम: $text")
        chat.add("Kivya: $answer")
        speak(answer)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) {
            listening = true
            listen({ words -> listening = false; process(words) }, { error -> listening = false; response = error })
        } else response = "Voice इस्तेमाल करने के लिए microphone permission चाहिए।"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Aqua, background = Night, surface = Card, onPrimary = Night)) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF18294A), Night))).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Aqua.copy(alpha = .13f), shape = RoundedCornerShape(15.dp), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.SmartToy, null, tint = Aqua, modifier = Modifier.size(28.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("KIVYA", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("VOICE • PHONE • AI TOOLS", color = Aqua, fontSize = 10.sp, letterSpacing = 1.4.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Surface(color = Lilac.copy(alpha = .09f), shape = CircleShape, modifier = Modifier.size(150.dp)) {}
                Surface(color = Aqua.copy(alpha = .12f), shape = CircleShape, modifier = Modifier.size(112.dp)) {}
                Surface(color = Card, shape = CircleShape, modifier = Modifier.size(78.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Aqua)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Aqua, modifier = Modifier.size(36.dp)) }
                }
            }
            Text(if (listening) "सुन रही हूँ..." else "तुम्हारी अपनी AI assistant", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(response, color = Color(0xFFBAC7DD), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "Phone", onClick = { mode = "Phone" }, label = { Text("Phone Assistant") })
                FilterChip(selected = mode == "AI", onClick = { mode = "AI" }, label = { Text("AI Tool Assistant") })
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp), placeholder = { Text("Kivya, mujhe suno...") })
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        listening = true
                        listen({ words -> listening = false; process(words) }, { error -> listening = false; response = error })
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Mic, null); Spacer(Modifier.width(7.dp)); Text(if (listening) "Listening" else "बोलें")
                }
                OutlinedButton(onClick = { if (prompt.isNotBlank()) process(prompt.trim()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Send") }
            }
            Spacer(Modifier.height(18.dp))
            Text("QUICK COMMANDS", color = Aqua, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Wi-Fi", "Settings", "YouTube").forEach { label ->
                    OutlinedButton(onClick = { process(when (label) { "Wi-Fi" -> "वाईफाई खोलो"; "Settings" -> "सेटिंग खोलो"; else -> "YouTube खोलो" }) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 3.dp)) { Text(label, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("RECENT CHAT", color = Aqua, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(chat.takeLast(7)) { line ->
                    Surface(color = Card.copy(alpha = .9f), shape = RoundedCornerShape(12.dp)) {
                        Text(line, color = Color(0xFFDDE6F5), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(11.dp))
                    }
                }
            }
            Text("Actions run only with Android permissions and user authorization.", color = Color(0xFF8290A9), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
