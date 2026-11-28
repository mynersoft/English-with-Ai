package com.example.data.local

import android.content.Context
import com.example.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object CourseMediaFileCache {
    private const val DIR_NAME = "course_offline_media"

    private fun cacheDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hashKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun extensionFor(url: String, defaultExt: String): String {
        val clean = url.substringBefore("?").substringAfterLast("/", "")
        val ext = clean.substringAfterLast(".", "").lowercase()
        return if (ext.matches(Regex("[a-z0-9]{2,5}"))) ".$ext" else defaultExt
    }

    fun getCachedFileIfExists(context: Context, url: String, defaultExt: String = ".bin"): File? {
        if (url.isBlank()) return null
        val file = File(cacheDir(context), "${hashKey(url)}${extensionFor(url, defaultExt)}")
        return if (file.exists() && file.length() > 0L) file else null
    }

    suspend fun downloadAndCache(
        context: Context,
        url: String,
        defaultExt: String = ".bin"
    ): File = withContext(Dispatchers.IO) {
        val existing = getCachedFileIfExists(context, url, defaultExt)
        if (existing != null) return@withContext existing

        val target = File(cacheDir(context), "${hashKey(url)}${extensionFor(url, defaultExt)}")
        val temp = File(cacheDir(context), "${target.name}.tmp")
        val req = Request.Builder().url(url).get().build()

        NetworkClient.admin.newCall(req).execute().use { res ->
            if (!res.isSuccessful) {
                throw IllegalStateException("Download failed (${res.code})")
            }
            val body = res.body ?: throw IllegalStateException("Empty download body")
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    }
}
