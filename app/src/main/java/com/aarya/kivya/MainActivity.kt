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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

private val Deep = Color(0xFF070B16)
private val Panel = Color(0xFF111B2D)
private val Aqua = Color(0xFF65F0DE)
private val Blue = Color(0xFF6C8CFF)
private val Muted = Color(0xFF91A2BA)

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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        engine.startListening(intent)
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
                // Never place a call silently: prefill the system dialer and let the user confirm.
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
    var response by remember { mutableStateOf("नमस्ते! मैं Kivya हूँ। बताओ, क्या करना है?") }
    var listening by remember { mutableStateOf(false) }
    val chat = remember { mutableStateListOf("Kivya: मैं तैयार हूँ। बोलकर या लिखकर command दो।") }

    fun process(text: String) {
        prompt = text
        val answer = if (mode == "Phone") phoneAction(text) else
            "AI Tools mode अभी backend से connect नहीं है। Cloud AI और developer tools अगली सुरक्षित integration में जुड़ेंगे।"
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

    MaterialTheme(colorScheme = darkColorScheme(primary = Aqua, background = Deep, surface = Panel, onPrimary = Deep)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF172744), Deep, Color(0xFF090D19))))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .imePadding().padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KivyaMark(Modifier.size(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("KIVYA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("YOUR PERSONAL AI COMPANION", color = Aqua, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                    Surface(color = Aqua.copy(alpha = .12f), shape = CircleShape) {
                        Text("● READY", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
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
                        Text(if (listening) "अपनी बात पूरी करें" else "आपकी आवाज़, आपके commands", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(response, color = Color(0xFFD5E1F2), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp), lineHeight = 19.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeCard("PHONE", "Device commands", mode == "Phone", Modifier.weight(1f)) { mode = "Phone" }
                    ModeCard("AI STUDIO", "AI tools & coding", mode == "AI", Modifier.weight(1f)) { mode = "AI" }
                }

                Spacer(Modifier.height(16.dp))
                Text("QUICK ACTIONS", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickAction("Wi-Fi", Icons.Default.Wifi, Modifier.weight(1f)) { process("Wi-Fi खोलो") }
                    QuickAction("Bluetooth", Icons.Default.Bluetooth, Modifier.weight(1f)) { process("Bluetooth खोलो") }
                    QuickAction("Settings", Icons.Default.Settings, Modifier.weight(1f)) { process("Settings खोलो") }
                }

                Spacer(Modifier.height(16.dp))
                Text("ASK KIVYA", color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Panel).border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(20.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = prompt, onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Kivya, YouTube खोलो...", color = Muted, fontSize = 13.sp) },
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
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            listening = true
                            listen({ words -> listening = false; process(words) }, { error -> listening = false; response = error })
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp).shadow(10.dp, CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFB45B72) else Aqua)
                ) {
                    Icon(Icons.Default.Mic, null, tint = Deep)
                    Spacer(Modifier.width(9.dp))
                    Text(if (listening) "सुन रही हूँ..." else "TALK TO KIVYA", color = Deep, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RECENT ACTIVITY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text("ON-DEVICE COMMANDS", color = Aqua.copy(alpha = .75f), fontSize = 9.sp)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(chat.takeLast(5).reversed()) { line ->
                        Surface(color = Color.White.copy(alpha = .045f), shape = RoundedCornerShape(13.dp)) {
                            Text(line, color = Color(0xFFDDE6F5), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp))
                        }
                    }
                }
                Text("Voice recognition may need your phone's offline language pack.", color = Muted.copy(alpha = .75f), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun KivyaMark(modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Aqua.copy(alpha = .2f), Blue.copy(alpha = .2f))), RoundedCornerShape(18.dp))
        .border(1.dp, Aqua.copy(alpha = .35f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Aqua, radius = size.minDimension * .34f, center = center, style = Stroke(width = 2.5.dp.toPx()))
            drawCircle(Blue, radius = size.minDimension * .18f, center = center)
            drawCircle(Color.White, radius = size.minDimension * .07f, center = center)
            drawArc(Aqua, 210f, 120f, false, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
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
