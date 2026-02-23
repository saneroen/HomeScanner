package org.nighthawklabs.homescanner.scanner

import android.graphics.PointF

data class FrameResult(
    val corners: List<PointF>,
    val confidence: Float,
    val blurScore: Float,
    val areaRatio: Float,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val rotationDegrees: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty(w: Int, h: Int): FrameResult = FrameResult(
            corners = emptyList(),
            confidence = 0f,
            blurScore = 0f,
            areaRatio = 0f,
            analysisWidth = w,
            analysisHeight = h,
            rotationDegrees = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}
