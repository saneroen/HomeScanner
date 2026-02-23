package org.nighthawklabs.homescanner.scanner

import android.graphics.Bitmap

object EdgeDetector {

    private val SOBEL_X = arrayOf(
        intArrayOf(-1, 0, 1),
        intArrayOf(-2, 0, 2),
        intArrayOf(-1, 0, 1)
    )

    private val SOBEL_Y = arrayOf(
        intArrayOf(-1, -2, -1),
        intArrayOf(0, 0, 0),
        intArrayOf(1, 2, 1)
    )

    fun toGrayscale(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val y = ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
            pixels[i] = y
        }
        return pixels
    }

    fun boxBlur3x3(gray: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += gray[(y + dy) * w + (x + dx)]
                    }
                }
                out[y * w + x] = (sum / 9).coerceIn(0, 255)
            }
        }
        for (x in 1 until w - 1) {
            out[x] = gray[x]
            out[(h - 1) * w + x] = gray[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            out[y * w] = gray[y * w]
            out[y * w + w - 1] = gray[y * w + w - 1]
        }
        return out
    }

    fun sobelMagnitude(gray: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var gx = 0
                var gy = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = gray[(y + dy) * w + (x + dx)]
                        gx += v * SOBEL_X[dy + 1][dx + 1]
                        gy += v * SOBEL_Y[dy + 1][dx + 1]
                    }
                }
                val mag = (kotlin.math.abs(gx) + kotlin.math.abs(gy)).coerceIn(0, 255)
                out[y * w + x] = mag
            }
        }
        return out
    }

    fun detectEdges(bitmap: Bitmap): IntArray {
        val gray = toGrayscale(bitmap)
        val w = bitmap.width
        val h = bitmap.height
        val blurred = boxBlur3x3(gray, w, h)
        return sobelMagnitude(blurred, w, h)
    }
}
