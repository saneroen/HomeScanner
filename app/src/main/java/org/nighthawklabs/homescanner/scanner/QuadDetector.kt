package org.nighthawklabs.homescanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF

data class QuadResult(
    val corners: List<PointF>,
    val confidence: Float,
    val isFallback: Boolean = false
) {
    init {
        require(corners.size == 4) { "Quad must have 4 corners" }
    }
}

object QuadDetector {

    private const val DETECTION_MAX_DIM = 800
    private const val MIN_QUAD_AREA = 2500
    private const val ASPECT_RATIO_MIN = 0.2f
    private const val ASPECT_RATIO_MAX = 5f
    private const val FALLBACK_INSET = 0.05f

    /** Detect quad from grayscale IntArray (0-255). Returns corners in analysis image coords. */
    fun detectQuadFromGray(gray: IntArray, w: Int, h: Int): QuadResult {
        val blurred = EdgeDetector.boxBlur3x3(gray, w, h)
        val edges = EdgeDetector.sobelMagnitude(blurred, w, h)
        val threshold = Binarizer.otsuThreshold(edges)
        var binary = Binarizer.binarize(edges, threshold)
        binary = Binarizer.dilate3x3(binary, w, h)

        val contours = ContourFinder.findContours(binary, w, h)
        var bestQuad: List<PointF>? = null
        var bestScore = 0f
        var bestArea = 0.0

        for (contour in contours) {
            val quad = ContourFinder.approximateQuad(contour)
            if (quad == null || quad.size != 4) continue

            val area = ContourFinder.polygonArea(quad)
            if (area < MIN_QUAD_AREA) continue

            val ordered = ContourFinder.orderCorners(quad) ?: continue
            val rectScore = computeRectangularity(ordered)
            val aspectOk = checkAspectRatio(ordered)
            if (!aspectOk) continue

            val score = (rectScore * (area / (w * h))).toFloat()
            if (score > bestScore && area > bestArea) {
                bestScore = score
                bestArea = area
                bestQuad = ordered
            }
        }

        return if (bestQuad != null && bestScore > 0.01f) {
            QuadResult(
                corners = bestQuad,
                confidence = bestScore.coerceIn(0f, 1f),
                isFallback = false
            )
        } else {
            fallbackQuad(w, h)
        }
    }

    fun detectQuad(bitmap: Bitmap): QuadResult {
        val scaled = if (maxOf(bitmap.width, bitmap.height) > DETECTION_MAX_DIM) {
            BitmapIO.scaleBitmapForDetection(bitmap)
        } else {
            bitmap
        }
        val scaleX = bitmap.width.toFloat() / scaled.width
        val scaleY = bitmap.height.toFloat() / scaled.height

        val edges = EdgeDetector.detectEdges(scaled)
        val threshold = Binarizer.otsuThreshold(edges)
        var binary = Binarizer.binarize(edges, threshold)
        val w = scaled.width
        val h = scaled.height
        binary = Binarizer.dilate3x3(binary, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val contours = ContourFinder.findContours(binary, w, h)
        var bestQuad: List<PointF>? = null
        var bestScore = 0f
        var bestArea = 0.0

        for (contour in contours) {
            val quad = ContourFinder.approximateQuad(contour)
            if (quad == null || quad.size != 4) continue

            val area = ContourFinder.polygonArea(quad)
            if (area < MIN_QUAD_AREA) continue

            val ordered = ContourFinder.orderCorners(quad) ?: continue
            val rectScore = computeRectangularity(ordered)
            val aspectOk = checkAspectRatio(ordered)
            if (!aspectOk) continue

            val score = (rectScore * (area / (w * h))).toFloat()
            if (score > bestScore && area > bestArea) {
                bestScore = score
                bestArea = area
                bestQuad = ordered.map { PointF(it.x * scaleX, it.y * scaleY) }
            }
        }

        return if (bestQuad != null && bestScore > 0.01f) {
            QuadResult(
                corners = bestQuad,
                confidence = bestScore.coerceIn(0f, 1f),
                isFallback = false
            )
        } else {
            fallbackQuad(bitmap.width, bitmap.height)
        }
    }

    private fun computeRectangularity(points: List<PointF>): Float {
        if (points.size != 4) return 0f
        var sum = 0f
        for (i in 0..3) {
            val a = points[i]
            val b = points[(i + 1) % 4]
            val c = points[(i + 2) % 4]
            val v1x = b.x - a.x
            val v1y = b.y - a.y
            val v2x = c.x - b.x
            val v2y = c.y - b.y
            val dot = v1x * v2x + v1y * v2y
            val len1 = kotlin.math.hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
            val len2 = kotlin.math.hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
            if (len1 < 1e-6f || len2 < 1e-6f) return 0f
            val cos = (dot / (len1 * len2)).coerceIn(-1f, 1f)
            val angleDeg = kotlin.math.acos(cos.toDouble()).toFloat() * 180f / Math.PI.toFloat()
            val dev = kotlin.math.abs(90f - angleDeg)
            sum += (1f - dev / 90f).coerceIn(0f, 1f)
        }
        return sum / 4
    }

    private fun checkAspectRatio(points: List<PointF>): Boolean {
        val w1 = kotlin.math.hypot(
            (points[1].x - points[0].x).toDouble(),
            (points[1].y - points[0].y).toDouble()
        ).toFloat()
        val w2 = kotlin.math.hypot(
            (points[2].x - points[3].x).toDouble(),
            (points[2].y - points[3].y).toDouble()
        ).toFloat()
        val h1 = kotlin.math.hypot(
            (points[3].x - points[0].x).toDouble(),
            (points[3].y - points[0].y).toDouble()
        ).toFloat()
        val h2 = kotlin.math.hypot(
            (points[2].x - points[1].x).toDouble(),
            (points[2].y - points[1].y).toDouble()
        ).toFloat()
        val width = (w1 + w2) / 2
        val height = (h1 + h2) / 2
        if (width < 1f || height < 1f) return false
        val ratio = width / height
        return ratio in ASPECT_RATIO_MIN..ASPECT_RATIO_MAX
    }

    private fun fallbackQuad(width: Int, height: Int): QuadResult {
        val insetX = width * FALLBACK_INSET
        val insetY = height * FALLBACK_INSET
        val corners = listOf(
            PointF(insetX, insetY),
            PointF(width - insetX, insetY),
            PointF(width - insetX, height - insetY),
            PointF(insetX, height - insetY)
        )
        return QuadResult(
            corners = corners,
            confidence = 0.3f,
            isFallback = true
        )
    }
}
