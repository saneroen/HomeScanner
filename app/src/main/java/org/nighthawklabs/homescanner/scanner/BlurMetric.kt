package org.nighthawklabs.homescanner.scanner

import kotlin.math.abs

/**
 * Computes blur/sharpness score from grayscale image.
 * Uses gradient energy (Sobel-like sum of absolute differences).
 * Higher value = sharper image.
 */
object BlurMetric {

    fun gradientEnergy(gray: IntArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var sum = 0L
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val v = { dy: Int, dx: Int -> gray[(y + dy) * w + (x + dx)].coerceIn(0, 255) }
                val gx = abs(
                    -v(-1, -1) + v(-1, 1) - 2 * v(0, -1) + 2 * v(0, 1) - v(1, -1) + v(1, 1)
                )
                val gy = abs(
                    -v(-1, -1) - 2 * v(-1, 0) - v(-1, 1) + v(1, -1) + 2 * v(1, 0) + v(1, 1)
                )
                sum += (gx.toLong() + gy).coerceIn(0, Int.MAX_VALUE.toLong())
            }
        }
        return sum.toFloat() / (w * h)
    }
}
