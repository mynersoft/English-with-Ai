package com.example.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object HandwritingImageHelper {

    fun createTempCameraImageUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "handwriting").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun calculateSampleSize(width: Int, height: Int, maxDim: Int = 1400): Int {
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

    suspend fun encodeUriToCompressedJpegBase64(
        context: Context,
        uri: Uri,
        maxDim: Int = 1400,
        quality: Int = 82
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: throw IOException("Could not read captured photo.")

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Invalid image captured.")
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IOException("Could not decode captured photo.")

            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { exifStream ->
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val oriented = applyExifRotation(rawBitmap, orientation)
            val finalBitmap = scaleDownIfNeeded(oriented, maxDim)
            if (oriented !== rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
            if (finalBitmap !== oriented && !oriented.isRecycled) oriented.recycle()

            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (!finalBitmap.isRecycled) finalBitmap.recycle()

            val bytes = out.toByteArray()
            if (bytes.isEmpty()) throw IOException("Compressed image is empty.")
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
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

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
