package com.mindisle.app.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageView
import java.io.File
import kotlin.math.max

data class CompanionBuiltinBackground(
    val displayName: String,
    val fileName: String
)

sealed class BackgroundImportResult {
    data class Success(val file: File) : BackgroundImportResult()
    object TooLarge : BackgroundImportResult()
    object UnsupportedFormat : BackgroundImportResult()
    object Failed : BackgroundImportResult()
}

class CompanionBackgroundManager(private val context: Context) {
    companion object {
        private const val TAG = "CompanionBackground"
        private const val PREFS_NAME = "companion_background_settings"
        private const val KEY_TYPE = "companion_background_type"
        private const val KEY_PATH = "companion_background_path"
        private const val TYPE_DEFAULT = "default"
        private const val TYPE_BUILTIN = "builtin"
        private const val TYPE_CUSTOM = "custom"
        private const val DEFAULT_FILE = "default.png"
        private const val MAX_FILE_SIZE = 10L * 1024L * 1024L
    }

    val builtinBackgrounds = listOf(
        CompanionBuiltinBackground("默认房间", DEFAULT_FILE),
        CompanionBuiltinBackground("明亮房间", "room.png"),
        CompanionBuiltinBackground("天空花园", "sky.png"),
        CompanionBuiltinBackground("星空夜晚", "night.png"),
        CompanionBuiltinBackground("校园窗边", "school.png")
    )

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val customBackgroundDirectory =
        File(context.filesDir, "backgrounds").apply { mkdirs() }

    fun applySavedBackground(imageView: ImageView) {
        val type = preferences.getString(KEY_TYPE, TYPE_DEFAULT) ?: TYPE_DEFAULT
        val path = preferences.getString(KEY_PATH, DEFAULT_FILE).orEmpty()
        val applied = when (type) {
            TYPE_CUSTOM -> applyCustomBackground(imageView, File(path), save = false)
            TYPE_BUILTIN -> applyBuiltinBackground(imageView, path, save = false)
            else -> applyBuiltinBackground(imageView, DEFAULT_FILE, save = false)
        }

        if (!applied) {
            Log.w(TAG, "Saved background is unavailable, restoring default")
            resetToDefault(imageView)
        }
    }

    fun selectBuiltin(imageView: ImageView, fileName: String): Boolean =
        applyBuiltinBackground(imageView, fileName, save = true)

    fun selectCustom(imageView: ImageView, file: File): Boolean =
        applyCustomBackground(imageView, file, save = true)

    fun resetToDefault(imageView: ImageView): Boolean =
        applyBuiltinBackground(imageView, DEFAULT_FILE, save = true, type = TYPE_DEFAULT)

    fun importFromGallery(uri: Uri): BackgroundImportResult {
        if (!isSupportedImage(uri)) {
            return BackgroundImportResult.UnsupportedFormat
        }

        val reportedSize = querySize(uri)
        if (reportedSize > MAX_FILE_SIZE) {
            return BackgroundImportResult.TooLarge
        }

        val extension = extensionFor(uri)
        val tempFile = File(customBackgroundDirectory, "background_import.tmp")
        val outputFile = File(
            customBackgroundDirectory,
            "background_${System.currentTimeMillis()}.$extension"
        )

        return try {
            var copiedBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        if (copiedBytes > MAX_FILE_SIZE) {
                            throw BackgroundTooLargeException()
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return BackgroundImportResult.Failed

            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            BackgroundImportResult.Success(outputFile)
        } catch (_: BackgroundTooLargeException) {
            tempFile.delete()
            outputFile.delete()
            BackgroundImportResult.TooLarge
        } catch (error: Exception) {
            tempFile.delete()
            outputFile.delete()
            Log.e(TAG, "Failed to import background", error)
            BackgroundImportResult.Failed
        }
    }

    private fun applyBuiltinBackground(
        imageView: ImageView,
        fileName: String,
        save: Boolean,
        type: String = TYPE_BUILTIN
    ): Boolean {
        val assetPath = "backgrounds/$fileName"
        val bitmap = decodeAssetBitmap(assetPath) ?: return false
        imageView.setImageBitmap(bitmap)
        if (save) {
            saveSelection(type, fileName)
        }
        return true
    }

    private fun applyCustomBackground(
        imageView: ImageView,
        file: File,
        save: Boolean
    ): Boolean {
        if (!file.isFile) return false
        val bitmap = decodeFileBitmap(file) ?: return false
        imageView.setImageBitmap(bitmap)
        if (save) {
            saveSelection(TYPE_CUSTOM, file.absolutePath)
        }
        return true
    }

    private fun saveSelection(type: String, path: String) {
        preferences.edit()
            .putString(KEY_TYPE, type)
            .putString(KEY_PATH, path)
            .apply()
    }

    private fun decodeAssetBitmap(assetPath: String): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
    }.onFailure {
        Log.e(TAG, "Unable to decode asset background: $assetPath", it)
    }.getOrNull()

    private fun decodeFileBitmap(file: File): Bitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.absolutePath, options)
    }.onFailure {
        Log.e(TAG, "Unable to decode custom background: ${file.absolutePath}", it)
    }.getOrNull()

    private fun calculateSampleSize(sourceWidth: Int, sourceHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        val metrics = context.resources.displayMetrics
        val targetWidth = max(1, metrics.widthPixels)
        val targetHeight = max(1, metrics.heightPixels)
        var sampleSize = 1
        while (sourceWidth / (sampleSize * 2) >= targetWidth &&
            sourceHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun isSupportedImage(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        if (mimeType in setOf("image/jpeg", "image/png", "image/webp")) return true
        val extension = queryDisplayName(uri)
            ?.substringAfterLast('.', "")
            ?.lowercase()
        return extension in setOf("jpg", "jpeg", "png", "webp")
    }

    private fun extensionFor(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> queryDisplayName(uri)
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
                ?: "jpg"
        }
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return -1L
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private class BackgroundTooLargeException : Exception()
}
