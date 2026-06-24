package com.mindisle.app.voice

interface VoiceRecognitionCallback {
    fun onStart()
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(message: String)
    fun onEnd()
}
