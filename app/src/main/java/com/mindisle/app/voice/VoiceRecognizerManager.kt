package com.mindisle.app.voice

import android.content.Context

interface VoiceRecognizerManager {
    fun init(context: Context)
    fun startListening(callback: VoiceRecognitionCallback)
    fun stopListening()
    fun cancel()
    fun release()
}
