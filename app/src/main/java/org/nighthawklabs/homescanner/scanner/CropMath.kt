package org.nighthawklabs.homescanner.scanner

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.atan2
import kotlin.math.hypot

object CropMath {

    data class FitResult(
        val imageRect: RectF,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    fun computeFit(
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Int,
        imageHeight: Int
    ): FitResult {
        val imageAspect = imageWidth.toFloat() / imageHeight
        val viewAspect = viewWidth / viewHeight
        val scale = if (viewAspect > imageAspect) {
            viewHeight / imageHeight
        } else {
            viewWidth / imageWidth
        }
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        val offsetX = (viewWidth - scaledW) / 2
        val offsetY = (viewHeight - scaledH) / 2
        return FitResult(
            imageRect = RectF(offsetX, offsetY, offsetX + scaledW, offsetY + scaledH),
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    fun screenToImage(screenX: Float, screenY: Float, fit: FitResult): PointF {
        val imgX = (screenX - fit.offsetX) / fit.scale
        val imgY = (screenY - fit.offsetY) / fit.scale
        return PointF(imgX, imgY)
    }

    fun imageToScreen(imgX: Float, imgY: Float, fit: FitResult): PointF {
        val screenX = imgX * fit.scale + fit.offsetX
        val screenY = imgY * fit.scale + fit.offsetY
        return PointF(screenX, screenY)
    }

    /** Transform-aware mapping: image coords -> screen coords with user zoom/pan */
    fun imageToScreen(
        p: PointF,
        fit: FitResult,
        userScale: Float,
        userOffset: Offset
    ): Offset {
        val baseRect = fit.imageRect
        val cx = baseRect.centerX()
        val cy = baseRect.centerY()
        val baseX = p.x * fit.scale + fit.offsetX
        val baseY = p.y * fit.scale + fit.offsetY
        val screenX = cx + (baseX - cx) * userScale + userOffset.x
        val screenY = cy + (baseY - cy) * userScale + userOffset.y
        return Offset(screenX, screenY)
    }

    /** Transform-aware mapping: screen coords -> image coords with user zoom/pan */
    fun screenToImage(
        screen: Offset,
        fit: FitResult,
        userScale: Float,
        userOffset: Offset
    ): PointF {
        val baseRect = fit.imageRect
        val cx = baseRect.centerX()
        val cy = baseRect.centerY()
        val baseX = cx + (screen.x - userOffset.x - cx) / userScale
        val baseY = cy + (screen.y - userOffset.y - cy) / userScale
        val imgX = (baseX - fit.offsetX) / fit.scale
        val imgY = (baseY - fit.offsetY) / fit.scale
        return PointF(imgX, imgY)
    }

    /** Transformed base rect in screen coords (for pan clamping) */
    fun transformedImageRect(
        fit: FitResult,
        userScale: Float,
        userOffset: Offset,
        viewWidth: Float,
        viewHeight: Float
    ): Rect {
        val baseRect = fit.imageRect
        val cx = baseRect.centerX()
        val cy = baseRect.centerY()
        val left = cx + (baseRect.left - cx) * userScale + userOffset.x
        val top = cy + (baseRect.top - cy) * userScale + userOffset.y
        val right = cx + (baseRect.right - cx) * userScale + userOffset.x
        val bottom = cy + (baseRect.bottom - cy) * userScale + userOffset.y
        return Rect(left, top, right, bottom)
    }

    fun constrainToBounds(p: PointF, width: Float, height: Float): PointF {
        return PointF(
            p.x.coerceIn(0f, width),
            p.y.coerceIn(0f, height)
        )
    }

    fun isSelfIntersecting(corners: List<PointF>): Boolean {
        if (corners.size != 4) return true
        val seg0a = corners[0] to corners[1]
        val seg0b = corners[2] to corners[3]
        val seg1a = corners[1] to corners[2]
        val seg1b = corners[3] to corners[0]
        return segmentsIntersect(seg0a.first, seg0a.second, seg0b.first, seg0b.second) ||
            segmentsIntersect(seg1a.first, seg1a.second, seg1b.first, seg1b.second)
    }

    private fun segmentsIntersect(
        a1: PointF, a2: PointF,
        b1: PointF, b2: PointF
    ): Boolean {
        val d1 = crossProduct(b1, a1, a2)
        val d2 = crossProduct(b2, a1, a2)
        val d3 = crossProduct(a1, b1, b2)
        val d4 = crossProduct(a2, b1, b2)
        if (d1 * d2 > 0 || d3 * d4 > 0) return false
        return true
    }

    private fun crossProduct(o: PointF, a: PointF, b: PointF): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    fun findNearestCorner(
        screenX: Float,
        screenY: Float,
        corners: List<PointF>,
        fit: FitResult,
        radiusPx: Float
    ): Int? = findNearestCornerTransformed(
        screenX, screenY, corners, fit, 1f, Offset.Zero, radiusPx
    )

    fun findNearestCornerTransformed(
        screenX: Float,
        screenY: Float,
        corners: List<PointF>,
        fit: FitResult,
        userScale: Float,
        userOffset: Offset,
        radiusPx: Float
    ): Int? {
        if (corners.isEmpty()) return null
        var minDist = Float.MAX_VALUE
        var nearest: Int? = null
        for (i in corners.indices) {
            val screen = imageToScreen(PointF(corners[i].x, corners[i].y), fit, userScale, userOffset)
            val dist = hypot((screenX - screen.x).toDouble(), (screenY - screen.y).toDouble()).toFloat()
            if (dist < radiusPx && dist < minDist) {
                minDist = dist
                nearest = i
            }
        }
        return nearest
    }

    /** Reorder corners to stable TL, TR, BR, BL (clockwise from top-left) */
    fun reorderCornersTLTRBRBL(corners: List<PointF>): List<PointF> {
        if (corners.size != 4) return corners
        val cx = corners.map { it.x }.average().toFloat()
        val cy = corners.map { it.y }.average().toFloat()
        val byAngle = corners.sortedBy { atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        val topLeft = byAngle.minByOrNull { it.x + it.y }!!
        val startIdx = byAngle.indexOf(topLeft)
        val rotated = (0..3).map { byAngle[(startIdx + it) % 4] }
        val cross = crossProduct(rotated[0], rotated[1], rotated[2])
        return if (cross > 0) rotated else listOf(rotated[0], rotated[3], rotated[2], rotated[1])
    }
}
