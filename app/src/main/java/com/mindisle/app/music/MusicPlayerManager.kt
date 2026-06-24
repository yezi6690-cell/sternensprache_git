package com.mindisle.app.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlin.random.Random

class MusicPlayerManager(
    context: Context,
    private val repository: MusicRepository
) {
    interface Listener {
        fun onTrackChanged(track: MusicTrack?)
        fun onPlaybackChanged(isPlaying: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var preparing = false
    private var pendingStart = false
    private var tracks = repository.loadTracks()
    private var currentIndex = tracks.indexOfFirst { it.id == repository.currentTrackId }
        .takeIf { it >= 0 }
        ?: if (tracks.isEmpty()) -1 else 0
    private var restorePositionTrackId: String? = repository.currentTrackId

    var listener: Listener? = null
    var playMode: MusicPlayMode = repository.playMode
        private set

    val currentTrack: MusicTrack?
        get() = tracks.getOrNull(currentIndex)

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    val currentPosition: Int
        get() = runCatching { player?.currentPosition ?: repository.lastPosition }
            .getOrDefault(0)

    val duration: Int
        get() = runCatching {
            player?.duration?.takeIf { it > 0 } ?: currentTrack?.duration?.toInt() ?: 0
        }.getOrDefault(0)

    fun getTracks(): List<MusicTrack> = tracks.toList()

    fun setTracks(newTracks: List<MusicTrack>) {
        val currentId = currentTrack?.id
        tracks = newTracks
        currentIndex = currentId?.let { id -> tracks.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: if (tracks.isEmpty()) -1 else 0
        if (tracks.isEmpty()) {
            stop()
        }
        repository.currentTrackId = currentTrack?.id
        listener?.onTrackChanged(currentTrack)
    }

    fun toggle() {
        if (tracks.isEmpty()) {
            listener?.onError("先添加一首本地音乐吧")
            return
        }
        if (isPlaying) {
            pause()
        } else if (player != null && !preparing) {
            runCatching { player?.start() }
                .onSuccess {
                    listener?.onPlaybackChanged(true)
                }
                .onFailure { play(currentIndex.coerceAtLeast(0)) }
        } else {
            play(currentIndex.coerceAtLeast(0))
        }
    }

    fun play() {
        if (tracks.isEmpty() || isPlaying) return
        if (player != null && !preparing) {
            runCatching { player?.start() }
                .onSuccess { listener?.onPlaybackChanged(true) }
                .onFailure { play(currentIndex.coerceAtLeast(0)) }
        } else {
            play(currentIndex.coerceAtLeast(0))
        }
    }

    fun play(index: Int) {
        val track = tracks.getOrNull(index) ?: return
        val restoredPosition = if (restorePositionTrackId == track.id) {
            repository.lastPosition.coerceAtLeast(0)
        } else {
            0
        }
        restorePositionTrackId = null
        releasePlayer(savePosition = false)
        currentIndex = index
        repository.currentTrackId = track.id
        repository.lastPosition = 0
        listener?.onTrackChanged(track)
        preparing = true
        pendingStart = true
        val newPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(appContext, Uri.parse(track.uri))
            setOnPreparedListener {
                preparing = false
                if (restoredPosition > 0 && restoredPosition < it.duration) {
                    it.seekTo(restoredPosition)
                    repository.lastPosition = restoredPosition
                }
                if (pendingStart) {
                    it.start()
                    listener?.onPlaybackChanged(true)
                }
            }
            setOnCompletionListener { handleCompletion() }
            setOnErrorListener { _, _, _ ->
                preparing = false
                listener?.onPlaybackChanged(false)
                listener?.onError("播放失败，可能是文件已移动或权限失效")
                true
            }
            prepareAsync()
        }
        player = newPlayer
    }

    fun pause() {
        runCatching {
            player?.pause()
            repository.lastPosition = currentPosition
        }
        pendingStart = false
        listener?.onPlaybackChanged(false)
    }

    fun previous() {
        if (tracks.isEmpty()) return
        val target = when (playMode) {
            MusicPlayMode.SHUFFLE -> randomIndex()
            else -> if (currentIndex <= 0) tracks.lastIndex else currentIndex - 1
        }
        play(target)
    }

    fun next() {
        if (tracks.isEmpty()) return
        val target = when (playMode) {
            MusicPlayMode.SHUFFLE -> randomIndex()
            else -> (currentIndex + 1).mod(tracks.size)
        }
        play(target)
    }

    fun seekTo(position: Int) {
        runCatching {
            player?.seekTo(position.coerceIn(0, duration.coerceAtLeast(0)))
            repository.lastPosition = position
        }
    }

    fun cycleMode(): MusicPlayMode {
        playMode = when (playMode) {
            MusicPlayMode.ORDER -> MusicPlayMode.SHUFFLE
            MusicPlayMode.SHUFFLE -> MusicPlayMode.REPEAT_ONE
            MusicPlayMode.REPEAT_ONE -> MusicPlayMode.ORDER
        }
        repository.playMode = playMode
        return playMode
    }

    fun setPlayMode(mode: MusicPlayMode): MusicPlayMode {
        playMode = mode
        repository.playMode = mode
        return playMode
    }

    fun removeTrack(trackId: String) {
        val removingCurrent = currentTrack?.id == trackId
        val newTracks = tracks.filterNot { it.id == trackId }
        if (removingCurrent) stop()
        setTracks(newTracks)
    }

    fun stop() {
        releasePlayer(savePosition = false)
        repository.lastPosition = 0
        listener?.onPlaybackChanged(false)
    }

    fun release() {
        repository.lastPosition = currentPosition
        releasePlayer(savePosition = true)
        listener = null
    }

    private fun handleCompletion() {
        when (playMode) {
            MusicPlayMode.REPEAT_ONE -> {
                player?.seekTo(0)
                player?.start()
            }
            MusicPlayMode.SHUFFLE -> play(randomIndex())
            MusicPlayMode.ORDER -> next()
        }
    }

    private fun randomIndex(): Int {
        if (tracks.size <= 1) return 0
        var nextIndex: Int
        do {
            nextIndex = Random.nextInt(tracks.size)
        } while (nextIndex == currentIndex)
        return nextIndex
    }

    private fun releasePlayer(savePosition: Boolean) {
        if (savePosition) {
            repository.lastPosition = currentPosition
        }
        pendingStart = false
        preparing = false
        runCatching { player?.stop() }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
    }
}
