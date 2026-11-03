package com.aarya.kivya

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

/**
 * Opt-in local wake-word listener. Microphone use is visible through a foreground
 * notification and can be stopped by the user at any time.
 */
class KivyaWakeWordService : Service() {
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var spotter: KeywordSpotter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListener()
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            if (!running) startListener()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start wake-word listener", e)
            stopListener()
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "KIVYA Wake Word", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KIVYA is listening")
            .setContentText("Wake-word detection is active. Tap Stop in KIVYA to turn it off.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startListener() {
        val modelDir = "kivya-kws-en"
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$modelDir/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$modelDir/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 1,
                provider = "cpu",
                modelType = "zipformer2"
            ),
            keywordsFile = "$modelDir/keywords.txt",
            keywordsScore = 1.5f,
            keywordsThreshold = 0.35f,
            numTrailingBlanks = 2
        )
        spotter = KeywordSpotter(assetManager = assets, config = config)
        val stream = spotter!!.createStream()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "AudioRecord buffer unavailable: $minBuffer" }
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, SAMPLE_RATE / 5 * 2)
        )
        require(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
        recorder = audio
        audio.startRecording()
        running = true
        worker = thread(name = "KIVYA-WakeWord") {
            val shortBuffer = ShortArray(SAMPLE_RATE / 10)
            try {
                while (running) {
                    val count = audio.read(shortBuffer, 0, shortBuffer.size)
                    if (count > 0) {
                        val samples = FloatArray(count) { shortBuffer[it] / 32768.0f }
                        stream.acceptWaveform(samples, SAMPLE_RATE)
                        while (spotter?.isReady(stream) == true) {
                            spotter?.decode(stream)
                            val detected = spotter?.getResult(stream)?.keyword.orEmpty()
                            if (detected.isNotBlank()) {
                                spotter?.reset(stream)
                                Log.i(TAG, "Wake word detected")
                                val launch = Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra(EXTRA_WAKE_WORD, true)
                                }
                                startActivity(launch)
                            }
                        }
                    } else if (count < 0) {
                        Log.e(TAG, "AudioRecord read failed: $count")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wake-word loop failed", e)
            } finally {
                try { stream.release() } catch (_: Exception) {}
                try { audio.stop() } catch (_: Exception) {}
                audio.release()
                recorder = null
                running = false
                stopSelf()
            }
        }
    }

    private fun stopListener() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        worker?.interrupt()
        worker = null
        try { spotter?.release() } catch (_: Exception) {}
        spotter = null
        recorder?.release()
        recorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopListener()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.aarya.kivya.WAKE_START"
        const val ACTION_STOP = "com.aarya.kivya.WAKE_STOP"
        const val EXTRA_WAKE_WORD = "kivya_wake_word"
        private const val TAG = "KIVYA-WakeWord"
        private const val CHANNEL_ID = "kivya_wake_word"
        private const val NOTIFICATION_ID = 4201
        private const val SAMPLE_RATE = 16000
    }
}
