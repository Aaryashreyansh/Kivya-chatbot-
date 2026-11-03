package com.aarya.kivya

import android.service.voice.VoiceInteractionService

/**
 * Android entry point for KIVYA when the user selects it as the system assistant.
 * Android binds this service only after the user explicitly grants the Assistant role.
 */
class KivyaVoiceInteractionService : VoiceInteractionService()
