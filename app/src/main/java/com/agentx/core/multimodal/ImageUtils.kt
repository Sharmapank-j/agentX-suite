package com.agentx.core.multimodal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImageMetadata(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fileSizeBytes: Long,
    val localFilePath: String
) {
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f
    val fileSizeKb: Long get() = fileSizeBytes / 1024
}

object ImageUtils {

    /**
     * Copies any content URI or gallery image stream safely to the app's internal storage
     * so it persists permanently and can be accessed without permission loss across restarts.
     */
    suspend fun persistImageToStorage(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val attachmentsDir = File(context.filesDir, "attachments").apply {
            if (!exists()) mkdirs()
        }
        val fileExtension = getExtensionFromMime(context, sourceUri)
        val targetFile = File(attachmentsDir, "img_${UUID.randomUUID()}.$fileExtension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open stream for URI: $sourceUri")

        targetFile.absolutePath
    }

    /**
     * Safely decodes a sampled Bitmap to prevent OutOfMemoryError on high-resolution camera photos.
     */
    fun decodeSampledBitmap(filePath: String, reqWidth: Int = 1024, reqHeight: Int = 1024): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Memory-efficient 16-bit format

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Encodes a sampled Bitmap into standard Base64 string for Multimodal API payload.
     */
    fun encodeToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Extracts dimensions and metadata from a local image file without loading all pixels.
     */
    fun extractMetadata(filePath: String): ImageMetadata {
        val file = File(filePath)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val mimeType = options.outMimeType ?: "image/jpeg"
        return ImageMetadata(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = mimeType,
            fileSizeBytes = if (file.exists()) file.length() else 0L,
            localFilePath = filePath
        )
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getExtensionFromMime(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        return when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            else -> "jpg"
        }
    }
}

/**
 * Compose helper to asynchronously decode a sampled ImageBitmap on a background thread.
 */
@Composable
fun rememberImageBitmapFromPath(filePath: String?, reqWidth: Int = 720, reqHeight: Int = 720): ImageBitmap? {
    if (filePath.isNullOrBlank()) return null
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val decoded = ImageUtils.decodeSampledBitmap(filePath, reqWidth, reqHeight)
                bitmap = decoded?.asImageBitmap()
            } catch (_: Throwable) {
                bitmap = null
            }
        }
    }

    return bitmap
}
