package org.nighthawklabs.homescanner.scanner

object Binarizer {

    fun otsuThreshold(pixels: IntArray): Int {
        val hist = IntArray(256)
        for (v in pixels) {
            val idx = v.coerceIn(0, 255)
            hist[idx]++
        }
        val total = pixels.size
        var sum = 0
        for (i in 0..255) sum += i * hist[i]
        var sumB = 0
        var wB = 0
        var wF: Int
        var maxVar = 0.0
        var threshold = 0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val varBetween = wB * wF * (mB - mF) * (mB - mF)
            if (varBetween > maxVar) {
                maxVar = varBetween
                threshold = t
            }
        }
        return threshold
    }

    fun binarize(pixels: IntArray, threshold: Int): IntArray {
        return IntArray(pixels.size) { i ->
            if (pixels[i] >= threshold) 255 else 0
        }
    }

    fun percentileThreshold(pixels: IntArray, percentile: Float = 0.9f): Int {
        val sorted = pixels.filter { it in 1..254 }.sorted()
        if (sorted.isEmpty()) return 128
        val idx = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    fun dilate3x3(binary: IntArray, w: Int, h: Int): IntArray {
        val out = binary.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (binary[y * w + x] == 255) continue
                var hasNeighbor = false
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (binary[(y + dy) * w + (x + dx)] == 255) {
                            hasNeighbor = true
                            break
                        }
                    }
                    if (hasNeighbor) break
                }
                if (hasNeighbor) out[y * w + x] = 255
            }
        }
        return out
    }
}
