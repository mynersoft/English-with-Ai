package com.example.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object AvatarImageHelper {
    private const val MAX_AVATAR_DIMENSION_PX = 512
    private const val MAX_INLINE_BYTES = 200 * 1024 // 200 KB compact target for fast upload & inline fallback

    fun createTempAvatarCameraUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "avatars_camera").apply { mkdirs() }
        // Clean stale camera captures older than 1 hour
        val cutoff = System.currentTimeMillis() - 3_600_000L
        dir.listFiles()?.forEach { old ->
            if (old.isFile && old.lastModified() < cutoff) {
                runCatching { old.delete() }
            }
        }
        val file = File(dir, "avatar_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun calculateSampleSize(width: Int, height: Int, maxDim: Int = MAX_AVATAR_DIMENSION_PX): Int {
        if (width <= 0 || height <= 0 || maxDim <= 0) return 1
        var inSampleSize = 1
        if (height > maxDim || width > maxDim) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= maxDim && (halfW / inSampleSize) >= maxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun prepareAvatarJpeg(
        context: Context,
        uri: Uri,
        maxDim: Int = MAX_AVATAR_DIMENSION_PX,
        initialQuality: Int = 86
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: throw IOException("Could not open the selected image.")

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Selected file is not a valid image.")
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IOException("Could not decode the selected photo.")

            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { exifStream ->
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val oriented = applyExifRotation(rawBitmap, orientation)
            val squareCropped = centerCropSquare(oriented)
            val finalBitmap = scaleDownIfNeeded(squareCropped, maxDim)

            if (oriented !== rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
            if (squareCropped !== oriented && !oriented.isRecycled) oriented.recycle()
            if (finalBitmap !== squareCropped && !squareCropped.isRecycled) squareCropped.recycle()

            var quality = initialQuality.coerceIn(60, 95)
            var bytes = compressJpeg(finalBitmap, quality)
            while (bytes.size > MAX_INLINE_BYTES && quality > 60) {
                quality = (quality - 10).coerceAtLeast(60)
                bytes = compressJpeg(finalBitmap, quality)
            }
            if (!finalBitmap.isRecycled) finalBitmap.recycle()

            if (bytes.isEmpty()) throw IOException("Processed avatar image is empty.")
            bytes
        }
    }

    fun saveLocalAvatarFile(context: Context, userId: String, jpegBytes: ByteArray): File {
        val safeUid = sanitizeUidForFile(userId)
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        // Remove previous local avatar files for this user so Coil never hits stale paths
        dir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.startsWith("avatar_${safeUid}_")) {
                runCatching { existing.delete() }
            }
        }
        val target = File(dir, "avatar_${safeUid}_${System.currentTimeMillis()}.jpg")
        target.writeBytes(jpegBytes)
        return target
    }

    fun clearLocalAvatarFiles(context: Context, userId: String) {
        val safeUid = sanitizeUidForFile(userId)
        val dir = File(context.filesDir, "avatars")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.startsWith("avatar_${safeUid}_")) {
                runCatching { existing.delete() }
            }
        }
    }

    fun isDataImageUri(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        return clean.startsWith("data:image/", ignoreCase = true) && clean.contains(";base64,", ignoreCase = true)
    }

    fun decodeDataUriBitmap(dataUri: String): ImageBitmap? {
        return runCatching {
            val idx = dataUri.indexOf(";base64,", ignoreCase = true)
            if (idx == -1) return null
            val b64 = dataUri.substring(idx + ";base64,".length)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun sanitizeUidForFile(userId: String): String {
        return userId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "student" }.take(40)
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w == h || w <= 0 || h <= 0) return bitmap
        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
