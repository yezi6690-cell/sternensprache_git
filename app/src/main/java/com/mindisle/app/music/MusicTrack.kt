package com.mindisle.app.music

data class MusicTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val coverPath: String?,
    val addedAt: Long
)

enum class MusicPlayMode {
    ORDER,
    SHUFFLE,
    REPEAT_ONE
}
