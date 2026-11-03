package com.aarya.kivya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.app.SearchManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Deep = Color(0xFF070B16)
private val Panel = Color(0xFF111B2D)
private val Aqua = Color(0xFF65F0DE)
private val Blue = Color(0xFF6C8CFF)
private val Muted = Color(0xFF91A2BA)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private lateinit var contactsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var callPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var pendingContactName: String? = null
    private var pendingCallNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val name = pendingContactName
            pendingContactName = null
            if (granted && !name.isNullOrBlank()) resolveAndCallContact(name)
            else say("Contacts permission नहीं मिली। कॉल नहीं की गई।")
        }
        callPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val number = pendingCallNumber
            pendingCallNumber = null
            if (granted && !number.isNullOrBlank()) placeDirectCall(number)
            else say("Phone permission नहीं मिली। कॉल नहीं की गई।")
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(0.96f)
                tts?.setPitch(1.04f)
                tts?.speak(
                    "हेलो आर्या, स्वागत है। मैं KIVYA हूँ। बताइए, आज मैं आपकी क्या मदद कर सकती हूँ?",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "kivya-welcome"
                )
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) speech = SpeechRecognizer.createSpeechRecognizer(this)
        setContent { KivyaHome(::startListening, ::say, ::phoneAction) }
    }

    private fun resolveAndCallContact(rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) { say("किसे कॉल करना है? नाम बताइए।"); return }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingContactName = name
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        val matches = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex)?.filter { it.isDigit() || it == '+' } ?: continue
                    if (number.isNotBlank() && matches.none { it.second == number }) matches.add(contactName to number)
                }
            }
        } catch (_: Exception) { say("Contacts पढ़ने में समस्या हुई।"); return }
        when {
            matches.isEmpty() -> say("$name नाम का contact नहीं मिला।")
            matches.size == 1 -> {
                say(matches[0].first + " को कॉल कर रही हूँ।")
                requestAndPlaceCall(matches[0].second)
            }
            else -> say("$name नाम के कई contacts मिले। पूरा नाम बोलिए, जैसे Rahul Kumar।")
        }
    }

    private fun requestAndPlaceCall(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeDirectCall(number)
        } else {
            pendingCallNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun placeDirectCall(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } catch (_: SecurityException) {
            say("कॉल की अनुमति नहीं मिली।")
        } catch (_: Exception) {
            say("फोन कॉल शुरू नहीं हो पाई।")
        }
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kivya-reply")
    }

    private fun startListening(onResult: (String) -> Unit, onFailure: (String) -> Unit) {
        val engine = speech ?: run { onFailure("इस फोन पर speech recognition उपलब्ध नहीं है।"); return }
        // Cancel any stale recognition session before starting a fresh, one-shot session.
        try { engine.cancel() } catch (_: Exception) {}
        var finished = false
        fun finish() {
            if (!finished) {
                finished = true
                try { engine.cancel() } catch (_: Exception) {}
            }
        }
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (finished) return
                finish()
                onFailure("आवाज़ पहचान नहीं पाई। इंटरनेट या Hindi voice service जाँचकर फिर बोलें।")
            }
            override fun onResults(results: Bundle?) {
                if (finished) return
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish()
                if (words.isNullOrBlank()) onFailure("कुछ सुनाई नहीं दिया। फिर कोशिश करें।") else onResult(words)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Kivya से बात करें")
            // Prefer online recognition when an offline Hindi model is not installed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            engine.startListening(intent)
        } catch (_: Exception) {
            finish()
            onFailure("Voice service शुरू नहीं हो पाई। Google app या speech service अपडेट करें।")
        }
    }

    private fun phoneAction(command: String): String {
        val c = command.lowercase(Locale.ROOT)
        return try {
            when {
                // Open the system Phone app so the user can review recent calls and call history.
                "कॉल हिस्ट्री" in c || "कॉल लॉग" in c || "recent calls" in c || "call history" in c || "call log" in c -> {
                    val phoneIntent = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_DIALER")
                    try {
                        startActivity(phoneIntent)
                        "फोन ऐप में Recent Calls खोल रही हूँ।"
                    } catch (_: Exception) {
                        startActivity(Intent(Intent.ACTION_DIAL))
                        "फोन डायलर खोल रही हूँ।"
                    }
                }
                // Explicit contact-call command: one unique contact match is called directly.
                (c.startsWith("call ") || c.startsWith("kivya call ") || c.startsWith("kivya, call ") ||
                    c.contains("कॉल करो") || c.contains("कॉल लगाओ") || c.contains("कॉल करना")) -> {
                    val contactName = command.replace(Regex("(?i)call|kivya|किव्या|किब्बया|कॉल करो|कॉल लगाओ|कॉल करना"), "").trim().trim(',', ' ')
                    if (contactName.isBlank()) {
                        startActivity(Intent(Intent.ACTION_DIAL))
                        "किसे कॉल करना है? नाम बताइए।"
                    } else {
                        resolveAndCallContact(contactName)
                        "$contactName को कॉल करने की कोशिश कर रही हूँ।"
                    }
                }
                // Numeric call requests continue to open the dialer for review.
                ("कॉल" in c || "call" in c || "डायल" in c || "dial" in c) -> {
                    val number = Regex("[+0-9][0-9 ()-]{4,}[0-9]").find(command)?.value
                        ?.filter { it.isDigit() || it == '+' }
                    if (number.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_DIAL))
                        "डायलर खोल रही हूँ। नंबर डालकर कॉल बटन दबाएँ।"
                    } else {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        "$number डायलर में तैयार है। कॉल करने के लिए फोन ऐप में Call दबाएँ।"
                    }
                }
                // Open Android's alarm UI; the user reviews and saves the alarm there.
                ("अलार्म" in c || "alarm" in c) -> {
                    val time = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(c)
                    if (time == null) {
                        startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                        "अलार्म ऐप खोल रही हूँ। समय चुनकर सेव करें।"
                    } else {
                        var hour = time.groupValues[1].toIntOrNull() ?: 7
                        val minute = time.groupValues[2].toIntOrNull() ?: 0
                        val suffix = time.groupValues[3].lowercase(Locale.ROOT)
                        if (suffix == "pm" && hour in 1..11) hour += 12
                        if (suffix == "am" && hour == 12) hour = 0
                        if (hour !in 0..23 || minute !in 0..59) {
                            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                            "समय समझ नहीं आया। अलार्म ऐप खोल रही हूँ।"
                        } else {
                            startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, "Kivya alarm"))
                            "अलार्म $hour बजकर $minute मिनट के लिए तैयार है। Clock ऐप में सेव करें।"
                        }
                    }
                }
                // Calendar event creation is delegated to the user's calendar app.
                ("कैलेंडर" in c || "calendar" in c || "इवेंट" in c || "event" in c) -> {
                    val eventTitle = command.replace(Regex("(?i)कैलेंडर|calendar|इवेंट|event|खोलो|खोलना|में|add|create|open"), "").trim()
                    val eventIntent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, eventTitle.ifBlank { "नया इवेंट" })
                    startActivity(eventIntent)
                    "Calendar में नया event तैयार कर रही हूँ। तारीख और समय जाँचकर सेव करें।"
                }
                // Send a search query to the system's web search handler.
                ("खोजो" in c || "ढूंढो" in c || "सर्च" in c || "search" in c || "find" in c) -> {
                    val query = command.replace(Regex("(?i)google|गूगल|पर|खोजो|ढूंढो|सर्च करो|सर्च|search for|search|find"), "").trim()
                    if (query.isBlank()) {
                        startActivity(Intent(Intent.ACTION_WEB_SEARCH))
                        "क्या खोजना है? Google search खोल रही हूँ।"
                    } else {
                        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
                        try { startActivity(searchIntent) } catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))) }
                        "$query खोज रही हूँ।"
                    }
                }
                "सेटिंग" in c || "settings" in c -> { startActivity(Intent(Settings.ACTION_SETTINGS)); "सेटिंग्स खोल रही हूँ।" }
                "वाईफाई" in c || "wifi" in c || "wi-fi" in c -> { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); "वाई-फाई सेटिंग्स खोल रही हूँ।" }
                "ब्लूटूथ" in c || "bluetooth" in c -> { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "ब्लूटूथ सेटिंग्स खोल रही हूँ।" }
                "यूट्यूब" in c || "youtube" in c -> launchApp("com.google.android.youtube", "YouTube", "https://www.youtube.com")
                "व्हाट्सएप" in c || "whatsapp" in c -> launchApp("com.whatsapp", "WhatsApp")
                "इंस्टाग्राम" in c || "instagram" in c -> launchApp("com.instagram.android", "Instagram")
                "कैमरा" in c || "camera" in c -> { startActivity(Intent("android.media.action.IMAGE_CAPTURE")); "कैमरा खोल रही हूँ।" }
                "फोन" in c || "डायलर" in c || "dialer" in c -> { startActivity(Intent(Intent.ACTION_DIAL)); "फोन खोल रही हूँ।" }
                "मैसेज" in c || "messages" in c -> { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)); "मैसेज खोल रही हूँ।" }
                "क्रोम" in c || "chrome" in c -> launchApp("com.android.chrome", "Chrome", "https://www.google.com")
                "गूगल" in c || "google" in c || "सर्च" in c -> openWeb("https://www.google.com", "Google खोल रही हूँ।")
                "मैप" in c || "नक्शा" in c || "maps" in c -> openWeb("https://maps.google.com", "Maps खोल रही हूँ।")
                "समय" in c || "टाइम" in c || "time" in c -> "अभी समय ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())} है।"
                else -> "यह command अभी उपलब्ध नहीं है। Settings, Wi-Fi, Bluetooth, YouTube, WhatsApp, Instagram, Camera, Phone, Chrome या Maps कहो।"
            }
        } catch (_: Exception) { "यह ऐप इस फोन में नहीं मिला या खुल नहीं पाया।" }
    }

    private fun launchApp(packageName: String, label: String, fallbackUrl: String? = null): String {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            return "$label खोल रही हूँ।"
        }
        if (fallbackUrl != null) return openWeb(fallbackUrl, "$label का वेब पेज खोल रही हूँ।")
        return "$label इस फोन में इंस्टॉल नहीं है।"
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
    var response by remember { mutableStateOf("हेलो आर्या, स्वागत है। मैं KIVYA हूँ। बताइए, आज मैं आपकी क्या मदद कर सकती हूँ?") }
    var listening by remember { mutableStateOf(false) }
    val chat = remember { mutableStateListOf("KIVYA: हेलो आर्या, स्वागत है। मैं आपकी मदद के लिए तैयार हूँ।") }

    val scope = rememberCoroutineScope()

    fun process(text: String) {
        prompt = text
        chat.add("तुम: $text")
        val normalized = text.lowercase(Locale.ROOT)
        val isDeviceCommand = listOf(
            "settings", "सेटिंग", "wifi", "wi-fi", "वाईफाई", "ब्लूटूथ", "bluetooth",
            "youtube", "यूट्यूब", "whatsapp", "व्हाट्सएप", "instagram", "इंस्टाग्राम",
            "camera", "कैमरा", "फोन", "phone", "dialer", "डायलर", "call ", "कॉल",
            "alarm", "अलार्म", "calendar", "कैलेंडर", "event", "इवेंट",
            "maps", "मैप", "नक्शा", "chrome", "क्रोम", "messages", "मैसेज",
            "call history", "कॉल हिस्ट्री", "कॉल लॉग", "recent calls",
            "खोजो", "ढूंढो", "सर्च", "search", "समय", "टाइम", "time"
        ).any { normalized.contains(it) }
        if (isDeviceCommand) {
            val answer = phoneAction(text)
            response = answer
            chat.add("KIVYA: $answer")
            speak(answer)
        } else {
            response = "KIVYA जवाब तैयार कर रही है..."
            scope.launch {
                val answer = withContext(Dispatchers.IO) {
                    try {
                        val connection = (URL("https://kivya-gemini-api.onrender.com/v1/chat").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 20000
                            readTimeout = 60000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        }
                        try {
                            val body = JSONObject().put("message", text).put("language", "auto").toString()
                            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                            val status = connection.responseCode
                            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                            val json = JSONObject(raw.ifBlank { "{}" })
                            if (status in 200..299) json.optString("reply").ifBlank { "Gemini से खाली जवाब मिला।" }
                            else json.optString("detail").ifBlank { "Server error ($status). थोड़ी देर बाद कोशिश करें।" }
                        } finally {
                            connection.disconnect()
                        }
                    } catch (_: Exception) {
                        "KIVYA server से संपर्क नहीं हो पाया। इंटरनेट जाँचकर फिर कोशिश करें।"
                    }
                }
                response = answer
                chat.add("KIVYA: $answer")
                speak(answer)
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) {
            listening = true
            listen({ words -> listening = false; process(words) }, { error -> listening = false; response = error })
        } else response = "Voice इस्तेमाल करने के लिए microphone permission चाहिए।"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Aqua, background = Deep, surface = Panel, onPrimary = Deep)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF172744), Deep, Color(0xFF090D19))))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp).padding(bottom = 82.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KivyaMark(Modifier.size(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("KIVYA", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                        Text("AI ASSISTANT", color = Aqua, fontSize = 9.sp, letterSpacing = 1.35.sp)
                    }
                    Surface(color = Color(0xFF182C43), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Aqua.copy(alpha = .28f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Box(Modifier.size(6.dp).background(Aqua, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("AI", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = .045f),
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        VoiceOrb(listening)
                        Text(if (listening) "मैं सुन रही हूँ..." else "नमस्ते, मैं Kivya हूँ", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(response, color = Color(0xFFD5E1F2), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp), lineHeight = 19.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Wi-Fi", Icons.Default.Wifi, Modifier.weight(1f)) { process("Wi-Fi खोलो") }
                    QuickAction("Bluetooth", Icons.Default.Bluetooth, Modifier.weight(1f)) { process("Bluetooth खोलो") }
                    QuickAction("Settings", Icons.Default.Settings, Modifier.weight(1f)) { process("Settings खोलो") }
                }

                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Panel).border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(20.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message KIVYA...", color = Muted, fontSize = 13.sp) },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = Aqua, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (prompt.isNotBlank()) process(prompt.trim()) })
                    )
                    IconButton(onClick = { if (prompt.isNotBlank()) process(prompt.trim()) }, modifier = Modifier.size(46.dp).background(Brush.linearGradient(listOf(Aqua, Blue)), CircleShape)) {
                        Icon(Icons.Default.ArrowUpward, "Send", tint = Deep)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CONVERSATION", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text("LIVE SESSION", color = Aqua.copy(alpha = .75f), fontSize = 9.sp)
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    chat.takeLast(5).reversed().forEach { line ->
                        Surface(color = Color.White.copy(alpha = .045f), shape = RoundedCornerShape(13.dp)) {
                            Text(line, color = Color(0xFFDDE6F5), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        listening = true
                        listen({ words -> listening = false; process(words) }, { error -> listening = false; response = error })
                    } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(56.dp).shadow(10.dp, CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFB45B72) else Aqua)
            ) {
                Icon(Icons.Default.Mic, null, tint = Deep)
                Spacer(Modifier.width(9.dp))
                Text(if (listening) "सुन रही हूँ..." else "TALK TO KIVYA", color = Deep, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            }
        }
    }
}

@Composable
private fun KivyaMark(modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Aqua.copy(alpha = .2f), Blue.copy(alpha = .2f))), RoundedCornerShape(18.dp))
        .border(1.dp, Aqua.copy(alpha = .35f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * .39f
            drawCircle(Aqua.copy(alpha = .16f), r, center)
            drawCircle(Brush.sweepGradient(listOf(Aqua, Blue, Color(0xFFB56BFF), Aqua)), r, center, style = Stroke(width = 2.6.dp.toPx()))
            drawArc(Blue, 205f, 125f, false, topLeft = Offset(center.x-r*.72f, center.y-r*.72f),
                size = androidx.compose.ui.geometry.Size(r*1.44f, r*1.44f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            val p = size.minDimension * .22f
            drawLine(Color.White, Offset(center.x-p*.65f, center.y-p), Offset(center.x-p*.65f, center.y+p), 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Aqua, Offset(center.x-p*.65f, center.y), Offset(center.x+p*.65f, center.y-p), 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Blue, Offset(center.x-p*.65f, center.y), Offset(center.x+p*.65f, center.y+p), 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 2.2.dp.toPx(), Offset(center.x+r*.76f, center.y-r*.2f))
        }
    }
}

@Composable
private fun VoiceOrb(listening: Boolean) {
    // Keep the orb still while idle to avoid a permanent animation/render loop.
    val pulse by animateFloatAsState(
        targetValue = if (listening) 1.08f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "pulse"
    )
    val spin = if (listening) 24f else 0f
    Box(Modifier.size(174.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension * .43f
            drawCircle(Aqua.copy(alpha = if (listening) .18f else .08f), base * pulse, c)
            drawCircle(Blue.copy(alpha = .12f), base * .78f, c, style = Stroke(width = 1.dp.toPx()))
            drawArc(Brush.sweepGradient(listOf(Aqua, Blue, Aqua.copy(alpha = .1f))), spin, 105f, false,
                topLeft = Offset(c.x - base * .88f, c.y - base * .88f),
                size = androidx.compose.ui.geometry.Size(base * 1.76f, base * 1.76f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF234A68), Color(0xFF101A31))), base * .62f, c)
        }
        Icon(Icons.Default.SmartToy, null, tint = Aqua, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Aqua.copy(alpha = .12f) else Color.White.copy(alpha = .035f),
        shape = RoundedCornerShape(17.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Aqua.copy(alpha = .55f) else Color.White.copy(alpha = .07f))
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = if (selected) Aqua else Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(subtitle, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Panel, shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
            Icon(icon, null, tint = Aqua, modifier = Modifier.size(21.dp))
            Text(label, color = Color(0xFFDCE7F6), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}
