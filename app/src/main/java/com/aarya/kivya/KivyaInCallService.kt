package com.aarya.kivya

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * Receives Telecom call state only while KIVYA is selected as the user's default dialer.
 * It never answers or rejects a call automatically. An explicit user voice command is required.
 */
object KivyaCallController {
    @Volatile private var ringingCall: Call? = null
    @Volatile private var activeCall: Call? = null

    internal fun onCallAdded(call: Call) {
        when (call.state) {
            Call.STATE_RINGING -> ringingCall = call
            Call.STATE_ACTIVE -> activeCall = call
        }
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_RINGING) ringingCall = call
                if (state == Call.STATE_ACTIVE) {
                    activeCall = call
                    if (ringingCall === call) ringingCall = null
                }
                if (state == Call.STATE_DISCONNECTED) {
                    if (ringingCall === call) ringingCall = null
                    if (activeCall === call) activeCall = null
                }
            }
        })
    }

    internal fun onCallRemoved(call: Call) {
        if (ringingCall === call) ringingCall = null
        if (activeCall === call) activeCall = null
    }

    fun answerIncomingCall(): Boolean {
        val call = ringingCall ?: return false
        if (call.state != Call.STATE_RINGING) return false
        return try {
            call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            true
        } catch (e: Exception) {
            Log.e("KIVYA-Call", "Could not answer incoming call", e)
            false
        }
    }

    fun rejectIncomingCall(): Boolean {
        val call = ringingCall ?: return false
        if (call.state != Call.STATE_RINGING) return false
        return try {
            call.reject(false, null)
            true
        } catch (e: Exception) {
            Log.e("KIVYA-Call", "Could not reject incoming call", e)
            false
        }
    }
}

class KivyaInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        KivyaCallController.onCallAdded(call)
        Log.i("KIVYA-Call", "Call added, state=${call.state}")
    }

    override fun onCallRemoved(call: Call) {
        KivyaCallController.onCallRemoved(call)
        super.onCallRemoved(call)
    }
}
