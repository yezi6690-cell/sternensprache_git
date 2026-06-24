package com.mindisle.app.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun loadTracks(): List<MusicTrack> {
        val raw = preferences.getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        MusicTrack(
                            id = item.optString("id"),
                            uri = item.optString("uri"),
                            title = item.optString("title"),
                            artist = item.optString("artist", "未知歌手"),
                            album = item.optString("album"),
                            duration = item.optLong("duration"),
                            coverPath = item.optString("coverPath").takeIf { it.isNotBlank() },
                            addedAt = item.optLong("addedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTracks(tracks: List<MusicTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject()
                    .put("id", track.id)
                    .put("uri", track.uri)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("album", track.album)
                    .put("duration", track.duration)
                    .put("coverPath", track.coverPath.orEmpty())
                    .put("addedAt", track.addedAt)
            )
        }
        preferences.edit().putString(KEY_TRACKS, array.toString()).apply()
    }

    var currentTrackId: String?
        get() = preferences.getString(KEY_CURRENT_TRACK, null)
        set(value) {
            preferences.edit().putString(KEY_CURRENT_TRACK, value).apply()
        }

    var lastPosition: Int
        get() = preferences.getInt(KEY_LAST_POSITION, 0)
        set(value) {
            preferences.edit().putInt(KEY_LAST_POSITION, value.coerceAtLeast(0)).apply()
        }

    var playMode: MusicPlayMode
        get() = runCatching {
            MusicPlayMode.valueOf(
                preferences.getString(KEY_PLAY_MODE, MusicPlayMode.ORDER.name)
                    ?: MusicPlayMode.ORDER.name
            )
        }.getOrDefault(MusicPlayMode.ORDER)
        set(value) {
            preferences.edit().putString(KEY_PLAY_MODE, value.name).apply()
        }

    companion object {
        private const val PREFS_NAME = "mindisle_music"
        private const val KEY_TRACKS = "tracks"
        private const val KEY_CURRENT_TRACK = "current_track"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_PLAY_MODE = "play_mode"
    }
}
