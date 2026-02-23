package org.nighthawklabs.homescanner.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

object BitmapIO {

    private const val MAX_PREVIEW_DIM = 1600
    private const val MAX_DETECTION_DIM = 800
    private const val MAX_OUTPUT_DIM = 2200

    fun decodeForPreview(file: File): Bitmap? = decodeWithMaxDimension(file, MAX_PREVIEW_DIM)

    fun decodeForDetection(file: File): Bitmap? = decodeWithMaxDimension(file, MAX_DETECTION_DIM)

    fun decodeFullForTransform(file: File, maxDim: Int = MAX_OUTPUT_DIM): Bitmap? =
        decodeWithMaxDimension(file, maxDim)

    private fun decodeWithMaxDimension(file: File, maxDim: Int): Bitmap? {
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, maxDim)
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sampleSize
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            bitmap = rotateBitmap(bitmap, orientation)
            bitmap
        } catch (e: Exception) {
            Log.e("BitmapIO", "Decode failed", e)
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val max = maxOf(width, height)
        if (max > maxDim) {
            inSampleSize = (max / maxDim).coerceAtLeast(1)
            var rounded = 1
            while (rounded < inSampleSize) rounded *= 2
            inSampleSize = rounded
        }
        return inSampleSize
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        ).also { if (it != bitmap) bitmap.recycle() }
    }

    fun saveAsJpeg(bitmap: Bitmap, file: File, quality: Int = 90): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            true
        } catch (e: Exception) {
            Log.e("BitmapIO", "Save failed", e)
            false
        }
    }

    fun scaleBitmapForDetection(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= MAX_DETECTION_DIM) return bitmap
        val scale = MAX_DETECTION_DIM.toFloat() / max
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
