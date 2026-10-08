package me.bmax.apatch.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.apApp
import okhttp3.Request
import java.io.File

/**
 * Saves a wallpaper into the user-visible gallery. On Android Q+ this uses
 * MediaStore (`Pictures/FolkPatch`); older releases fall back to the app's
 * external pictures directory (no extra runtime permission required).
 */
object WallpaperDownloader {

    sealed interface Result {
        data class Success(val uri: Uri, val location: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun download(context: Context, url: String, fileName: String): Result =
        withContext(Dispatchers.IO) {
            val bytes = runCatching {
                val source = Uri.parse(url)
                if (source.scheme == "file" || source.scheme == "content") {
                    context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                        ?: error("cannot open cached image")
                } else {
                    apApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        response.body.bytes()
                    }
                }
            }.getOrElse { return@withContext Result.Failure(it.message ?: "download failed") }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, fileName, bytes)
                } else {
                    saveToAppPictures(context, fileName, bytes)
                }
            }.getOrElse { Result.Failure(it.message ?: "save failed") }
        }

    private fun saveToMediaStore(context: Context, fileName: String, bytes: ByteArray): Result {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FolkPatch")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create image")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("cannot open image")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return Result.Success(uri, "Pictures/FolkPatch")
    }

    private fun saveToAppPictures(context: Context, fileName: String, bytes: ByteArray): Result {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val dir = File(root, "FolkPatch").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return Result.Success(Uri.fromFile(file), file.parent ?: "app pictures")
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/*"
    }
}
