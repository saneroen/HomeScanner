package org.nighthawklabs.homescanner.scanner

import android.graphics.Bitmap

object ImageEnhancer {

    fun enhance(
        bitmap: Bitmap,
        grayscale: Boolean = false,
        contrastStretch: Boolean = true,
        binarize: Boolean = false
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        if (grayscale) {
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val y = ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
                pixels[i] = 0xFF000000.toInt() or (y shl 16) or (y shl 8) or y
            }
        }

        if (contrastStretch) {
            val gray = IntArray(pixels.size) { (pixels[it] shr 16) and 0xFF }
            val sorted = gray.sorted()
            val lowIdx = (sorted.size * 0.02).toInt().coerceIn(0, sorted.size - 1)
            val highIdx = (sorted.size * 0.98).toInt().coerceIn(0, sorted.size - 1)
            val pLow = sorted[lowIdx]
            val pHigh = sorted[highIdx]
            val range = (pHigh - pLow).coerceAtLeast(1)
            for (i in pixels.indices) {
                val r = (pixels[i] shr 16) and 0xFF
                val g = (pixels[i] shr 8) and 0xFF
                val b = pixels[i] and 0xFF
                val nr = ((r - pLow) * 255 / range).coerceIn(0, 255)
                val ng = ((g - pLow) * 255 / range).coerceIn(0, 255)
                val nb = ((b - pLow) * 255 / range).coerceIn(0, 255)
                pixels[i] = 0xFF000000.toInt() or (nr shl 16) or (ng shl 8) or nb
            }
        }

        if (binarize) {
            val gray = IntArray(pixels.size) {
                val p = pixels[it]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
            }
            val threshold = Binarizer.otsuThreshold(gray)
            for (i in pixels.indices) {
                val v = if (gray[i] >= threshold) 255 else 0
                pixels[i] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
