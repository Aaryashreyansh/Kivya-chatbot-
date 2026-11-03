package com.aarya.kivya

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Minimal assistant session. Invoking the system assistant opens KIVYA's existing
 * conversation screen. The session is intentionally user-initiated; no hidden
 * microphone capture or background recording is started here.
 */
class KivyaVoiceInteractionSession(context: android.content.Context) :
    VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startAssistantActivity(launch)
        hide()
    }
}
