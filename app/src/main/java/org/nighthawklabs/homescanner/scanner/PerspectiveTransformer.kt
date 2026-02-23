package org.nighthawklabs.homescanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object PerspectiveTransformer {

    private const val MAX_OUTPUT_DIM = 2200

    fun transform(source: Bitmap, corners: List<PointF>): Bitmap {
        require(corners.size == 4) { "Need 4 corners" }
        val ordered = orderCornersTLTRBRBL(corners)
        val tl = ordered[0]
        val tr = ordered[1]
        val br = ordered[2]
        val bl = ordered[3]

        val outWidth = max(
            distance(tl, tr).toInt(),
            distance(bl, br).toInt()
        )
        val outHeight = max(
            distance(tl, bl).toInt(),
            distance(tr, br).toInt()
        )

        var w = outWidth
        var h = outHeight
        var scale = 1f
        if (max(w, h) > MAX_OUTPUT_DIM) {
            scale = MAX_OUTPUT_DIM.toFloat() / max(w, h)
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }
        w = w.coerceAtLeast(1)
        h = h.coerceAtLeast(1)

        val destCorners = listOf(
            PointF(0f, 0f),
            PointF(w.toFloat(), 0f),
            PointF(w.toFloat(), h.toFloat()),
            PointF(0f, h.toFloat())
        )

        val H = computeHomography(destCorners, corners)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcW = source.width
        val srcH = source.height

        val srcPixels = IntArray(srcW * srcH)
        source.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val src = applyHomography(H, x.toFloat(), y.toFloat())
                val sx = src.x
                val sy = src.y

                val pixel = if (sx in 0f..(srcW - 1).toFloat() && sy in 0f..(srcH - 1).toFloat()) {
                    bilinearSample(srcPixels, srcW, srcH, sx, sy)
                } else {
                    0xFFFFFFFF.toInt()
                }
                outPixels[y * w + x] = pixel
            }
        }

        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }

    private fun orderCornersTLTRBRBL(corners: List<PointF>): List<PointF> {
        val topTwo = corners.sortedBy { it.y }.take(2).sortedBy { it.x }
        val bottomTwo = corners.sortedBy { it.y }.takeLast(2).sortedBy { it.x }
        return listOf(
            topTwo[0],
            topTwo[1],
            bottomTwo[1],
            bottomTwo[0]
        )
    }

    private fun distance(a: PointF, b: PointF): Float {
        return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    }

    private fun computeHomography(dest: List<PointF>, src: List<PointF>): FloatArray {
        val A = Array(8) { FloatArray(8) }
        val b = FloatArray(8)

        for (i in 0..3) {
            val x = dest[i].x
            val y = dest[i].y
            val u = src[i].x
            val v = src[i].y
            A[2 * i][0] = x
            A[2 * i][1] = y
            A[2 * i][2] = 1f
            A[2 * i][3] = 0f
            A[2 * i][4] = 0f
            A[2 * i][5] = 0f
            A[2 * i][6] = -u * x
            A[2 * i][7] = -u * y
            b[2 * i] = u

            A[2 * i + 1][0] = 0f
            A[2 * i + 1][1] = 0f
            A[2 * i + 1][2] = 0f
            A[2 * i + 1][3] = x
            A[2 * i + 1][4] = y
            A[2 * i + 1][5] = 1f
            A[2 * i + 1][6] = -v * x
            A[2 * i + 1][7] = -v * y
            b[2 * i + 1] = v
        }

        solve8x8(A, b)
        return floatArrayOf(
            b[0], b[1], b[2],
            b[3], b[4], b[5],
            b[6], b[7], 1f
        )
    }

    private fun solve8x8(A: Array<FloatArray>, b: FloatArray) {
        val n = 8
        for (col in 0 until n) {
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(A[row][col]) > abs(A[maxRow][col])) maxRow = row
            }
            A[col] = A[maxRow].also { A[maxRow] = A[col] }
            b[col] = b[maxRow].also { b[maxRow] = b[col] }
            val pivot = A[col][col]
            if (abs(pivot) < 1e-10f) continue
            for (j in col until n) A[col][j] /= pivot
            b[col] /= pivot
            for (i in 0 until n) {
                if (i != col && abs(A[i][col]) > 1e-10f) {
                    val factor = A[i][col]
                    for (j in col until n) A[i][j] -= factor * A[col][j]
                    b[i] -= factor * b[col]
                }
            }
        }
    }

    private fun applyHomography(H: FloatArray, x: Float, y: Float): PointF {
        val w = H[6] * x + H[7] * y + H[8]
        if (abs(w) < 1e-10f) return PointF(-1f, -1f)
        val u = (H[0] * x + H[1] * y + H[2]) / w
        val v = (H[3] * x + H[4] * y + H[5]) / w
        return PointF(u, v)
    }

    private fun bilinearSample(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, w - 1)
        val y0 = y.toInt().coerceIn(0, h - 1)
        val x1 = (x0 + 1).coerceIn(0, w - 1)
        val y1 = (y0 + 1).coerceIn(0, h - 1)
        val fx = x - x0
        val fy = y - y0
        val p00 = pixels[y0 * w + x0]
        val p10 = pixels[y0 * w + x1]
        val p01 = pixels[y1 * w + x0]
        val p11 = pixels[y1 * w + x1]
        val r = (1 - fx) * (1 - fy) * ((p00 shr 16) and 0xFF) +
            fx * (1 - fy) * ((p10 shr 16) and 0xFF) +
            (1 - fx) * fy * ((p01 shr 16) and 0xFF) +
            fx * fy * ((p11 shr 16) and 0xFF)
        val g = (1 - fx) * (1 - fy) * ((p00 shr 8) and 0xFF) +
            fx * (1 - fy) * ((p10 shr 8) and 0xFF) +
            (1 - fx) * fy * ((p01 shr 8) and 0xFF) +
            fx * fy * ((p11 shr 8) and 0xFF)
        val b = (1 - fx) * (1 - fy) * (p00 and 0xFF) +
            fx * (1 - fy) * (p10 and 0xFF) +
            (1 - fx) * fy * (p01 and 0xFF) +
            fx * fy * (p11 and 0xFF)
        return 0xFF000000.toInt() or
            ((r.toInt().coerceIn(0, 255)) shl 16) or
            ((g.toInt().coerceIn(0, 255)) shl 8) or
            (b.toInt().coerceIn(0, 255))
    }
}
