package com.mindisle.app.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MusicMetadataUtils {
    fun read(context: Context, uri: Uri): MusicTrack? = runCatching {
        val id = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val fileName = queryDisplayName(context, uri).orEmpty()
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.').ifBlank { "本地音乐" }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: "未知歌手"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                .orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val coverPath = retriever.embeddedPicture?.let {
                saveCover(context, id, it)
            }
            MusicTrack(
                id = id,
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                coverPath = coverPath,
                addedAt = System.currentTimeMillis()
            )
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun saveCover(context: Context, id: String, bytes: ByteArray): String? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching null
            val directory = File(context.filesDir, "music_covers").apply { mkdirs() }
            val file = File(directory, "$id.jpg")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it)
            }
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
}
