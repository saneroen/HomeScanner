package org.nighthawklabs.homescanner.scanner

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset

/**
 * Maps quad corners from analysis image coordinates to preview overlay (screen) coordinates.
 * Use PreviewView with ScaleType.FILL_CENTER for straightforward linear mapping.
 */
object OverlayMapper {

    /**
     * @param corners Analysis-space points (PointF)
     * @param analysisWidth Analysis image width
     * @param analysisHeight Analysis image height
     * @param previewWidth Preview overlay width (Compose layout pixels)
     * @param previewHeight Preview overlay overlay height
     * @param rotationDegrees Sensor rotation (0, 90, 180, 270)
     */
    fun analysisToPreview(
        corners: List<PointF>,
        analysisWidth: Int,
        analysisHeight: Int,
        previewWidth: Float,
        previewHeight: Float,
        rotationDegrees: Int = 0
    ): List<Offset> {
        val (aw, ah) = when (rotationDegrees) {
            90, 270 -> analysisHeight to analysisWidth
            else -> analysisWidth to analysisHeight
        }
        val scale = minOf(previewWidth / aw, previewHeight / ah)
        val drawW = aw * scale
        val drawH = ah * scale
        val offsetX = (previewWidth - drawW) / 2
        val offsetY = (previewHeight - drawH) / 2

        val (srcW, srcH) = analysisWidth.toFloat() to analysisHeight.toFloat()

        return corners.map { p ->
            val (x, y) = when (rotationDegrees) {
                90 -> p.y to (srcW - p.x)
                180 -> (srcW - p.x) to (srcH - p.y)
                270 -> (srcH - p.y) to p.x
                else -> p.x to p.y
            }
            Offset(
                offsetX + x * scale,
                offsetY + y * scale
            )
        }
    }
}
