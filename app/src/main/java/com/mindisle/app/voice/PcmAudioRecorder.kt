package com.mindisle.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class PcmAudioRecorder(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val frameSize: Int = 1280
) {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val recording = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(
        onFrame: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        if (recording.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError("需要麦克风权限才能使用语音输入")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onError("麦克风启动失败，请检查权限")
            return
        }

        val bufferSize = maxOf(minBuffer, frameSize * 2)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: RuntimeException) {
            onError("麦克风启动失败，请检查权限")
            return
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError("麦克风启动失败，请检查权限")
            return
        }

        recorder = audioRecord
        recording.set(true)
        worker = Thread {
            try {
                audioRecord.startRecording()
                val buffer = ByteArray(frameSize)
                while (recording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        onFrame(buffer.copyOf(read))
                    }
                }
            } catch (_: RuntimeException) {
                onError("录音失败")
            } finally {
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: RuntimeException) {
                }
                audioRecord.release()
            }
        }.apply {
            name = "xinyu-pcm-recorder"
            start()
        }
    }

    fun stop() {
        recording.set(false)
        if (Thread.currentThread() != worker) {
            worker?.join(500)
        }
        worker = null
        recorder = null
    }
}
