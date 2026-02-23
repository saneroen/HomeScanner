package org.nighthawklabs.homescanner.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_DIMENSION = 1600

data class MlKitExtractResult(val text: Text, val imageWidth: Int, val imageHeight: Int)

/**
 * Extracts text using ML Kit with upright bitmap to ensure consistent coordinates.
 * Decodes image, applies Exif rotation, downsamples if needed, then passes to ML Kit.
 * Boxes correspond to the upright bitmap dimensions.
 */
class MlKitTextExtractor {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractText(context: Context, file: File): MlKitExtractResult =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                throw IllegalArgumentException("Image file not found: ${file.absolutePath}")
            }
            val (bitmap, width, height) = loadUprightBitmap(file)
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val text = recognizer.process(image).await()
                MlKitExtractResult(text, width, height)
            } finally {
                bitmap.recycle()
            }
        }

    private fun loadUprightBitmap(file: File): Triple<Bitmap, Int, Int> {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val origW = opts.outWidth ?: 1000
        val origH = opts.outHeight ?: 1000

        val sampleSize = if (maxOf(origW, origH) > MAX_DIMENSION) {
            val max = maxOf(origW, origH)
            var s = 1
            while (max / (s * 2) > MAX_DIMENSION) s *= 2
            s
        } else 1

        val decodeOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: throw IllegalArgumentException("Failed to decode image")
        val w = bitmap.width
        val h = bitmap.height

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true).also {
                bitmap.recycle()
            }
        } else bitmap

        return Triple(rotated, rotated.width, rotated.height)
    }
}
