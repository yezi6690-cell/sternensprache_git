package com.mindisle.app.relax

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class WhiteNoisePlayerController(context: Context) {
    interface Listener {
        fun onPrepared(durationMs: Int)
        fun onPlaybackChanged(isPlaying: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var preparing = false
    private var pendingPlay = false
    private var volume = 0.72f
    private var looping = true

    var listener: Listener? = null

    val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    val currentPosition: Int
        get() = runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(0)

    val duration: Int
        get() = runCatching { mediaPlayer?.duration?.coerceAtLeast(0) ?: 0 }.getOrDefault(0)

    fun prepare() {
        if (prepared) {
            listener?.onPrepared(duration)
            listener?.onPlaybackChanged(isPlaying)
            return
        }
        if (preparing) return

        preparing = true
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                appContext.assets.openFd(ASSET_PATH).use { descriptor ->
                    setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                }
                isLooping = looping
                setVolume(volume, volume)
                setOnPreparedListener { player ->
                    preparing = false
                    prepared = true
                    listener?.onPrepared(player.duration.coerceAtLeast(0))
                    if (pendingPlay) {
                        pendingPlay = false
                        player.start()
                        listener?.onPlaybackChanged(true)
                    } else {
                        listener?.onPlaybackChanged(false)
                    }
                }
                setOnCompletionListener {
                    listener?.onPlaybackChanged(false)
                }
                setOnErrorListener { _, _, _ ->
                    preparing = false
                    prepared = false
                    pendingPlay = false
                    listener?.onPlaybackChanged(false)
                    listener?.onError("白噪音暂时无法播放，请稍后重试")
                    true
                }
                prepareAsync()
            }
        }.onSuccess {
            mediaPlayer = it
        }.onFailure {
            preparing = false
            pendingPlay = false
            listener?.onError("内置白噪音资源读取失败")
        }
    }

    fun play() {
        if (!prepared) {
            pendingPlay = true
            prepare()
            return
        }
        runCatching {
            mediaPlayer?.start()
        }.onSuccess {
            listener?.onPlaybackChanged(true)
        }.onFailure {
            listener?.onError("白噪音暂时无法播放，请稍后重试")
        }
    }

    fun pause() {
        pendingPlay = false
        if (isPlaying) {
            runCatching { mediaPlayer?.pause() }
        }
        listener?.onPlaybackChanged(false)
    }

    fun seekTo(positionMs: Int) {
        if (!prepared) return
        runCatching {
            mediaPlayer?.seekTo(positionMs.coerceIn(0, duration.coerceAtLeast(0)))
        }
    }

    fun setVolume(progress: Int) {
        volume = (progress.coerceIn(0, 100) / 100f)
        runCatching { mediaPlayer?.setVolume(volume, volume) }
    }

    fun setLooping(enabled: Boolean) {
        looping = enabled
        runCatching { mediaPlayer?.isLooping = enabled }
    }

    fun release() {
        pendingPlay = false
        preparing = false
        prepared = false
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.reset() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        listener = null
    }

    companion object {
        const val ASSET_PATH = "audio/white_noise/ten_hour_sleep_dolby.mp3"
    }
}
