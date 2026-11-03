package com.aarya.kivya

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Wake-word runtime entry point.
 * The listener is started only after explicit user action and microphone permission.
 * Model initialization is intentionally guarded until verified model assets are packaged.
 */
class KivyaWakeWordService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("KIVYA-WakeWord", "Wake-word service requested")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
