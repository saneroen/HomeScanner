package org.nighthawklabs.homescanner.scanner

import android.graphics.ImageFormat
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.min

private const val MAX_ANALYSIS_DIM = 480

/**
 * Converts ImageProxy (YUV) to downscaled grayscale and runs quad detection.
 * Reuses buffers where possible. Returns FrameResult in analysis image coordinates.
 */
class FrameProcessor {

    private var grayBuffer: IntArray? = null

    fun process(image: ImageProxy, rotationDegrees: Int): FrameResult? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val yPlane = image.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val srcW = image.width
        val srcH = image.height

        val scale = min(1f, MAX_ANALYSIS_DIM.toFloat() / maxOf(srcW, srcH))
        val w = (srcW * scale).toInt().coerceAtLeast(64).coerceAtMost(srcW)
        val h = (srcH * scale).toInt().coerceAtLeast(64).coerceAtMost(srcH)

        val size = w * h
        if (grayBuffer == null || grayBuffer!!.size < size) {
            grayBuffer = IntArray(size)
        }
        val gray = grayBuffer!!

        val stepX = (srcW.toFloat() / w).toInt().coerceAtLeast(1)
        val stepY = (srcH.toFloat() / h).toInt().coerceAtLeast(1)

        for (dy in 0 until h) {
            val sy = (dy * stepY).coerceIn(0, srcH - 1)
            val rowStart = sy * yRowStride
            for (dx in 0 until w) {
                val sx = (dx * stepX).coerceIn(0, srcW - 1)
                val offset = rowStart + sx * yPixelStride
                val y = yBuffer.get(offset).toInt() and 0xFF
                gray[dy * w + dx] = y
            }
        }

        val blurScore = BlurMetric.gradientEnergy(gray, w, h)
        val quadResult = QuadDetector.detectQuadFromGray(gray, w, h)

        val areaRatio = if (quadResult.corners.isNotEmpty()) {
            val area = ContourFinder.polygonArea(quadResult.corners)
            (area / (w * h)).toFloat()
        } else 0f

        return FrameResult(
            corners = quadResult.corners,
            confidence = quadResult.confidence,
            blurScore = blurScore,
            areaRatio = areaRatio,
            analysisWidth = w,
            analysisHeight = h,
            rotationDegrees = rotationDegrees,
            timestamp = System.currentTimeMillis()
        )
    }
}
