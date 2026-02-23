package org.nighthawklabs.homescanner.ui.receipt.debug

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import org.nighthawklabs.homescanner.data.parser.debug.DebugLine
import org.nighthawklabs.homescanner.data.parser.debug.DebugRow

@Composable
fun DebugOverlayCanvas(
    modifier: Modifier = Modifier,
    imagePixelWidth: Int,
    imagePixelHeight: Int,
    displayImageWidth: Float,
    displayImageHeight: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    lines: List<DebugLine>,
    rows: List<DebugRow> = emptyList(),
    selectedRowIndex: Int? = null,
    drawRowBoxes: Boolean = false
) {
    if (imagePixelWidth <= 0 || imagePixelHeight <= 0) return
    val scaleX = displayImageWidth / imagePixelWidth
    val scaleY = displayImageHeight / imagePixelHeight

    fun mapRect(left: Float, top: Float, right: Float, bottom: Float): Pair<Offset, Size> {
        val x = imageOffsetX + left * scaleX
        val y = imageOffsetY + top * scaleY
        val w = (right - left) * scaleX
        val h = (bottom - top) * scaleY
        return Offset(x, y) to Size(w, h)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        for ((idx, line) in lines.withIndex()) {
            val l = line.left ?: continue
            val t = line.top ?: continue
            val r = line.right ?: continue
            val b = line.bottom ?: continue
            if (r <= l || b <= t) continue
            val (offset, size) = mapRect(l, t, r, b)
            drawRect(
                color = Color.Green.copy(alpha = 0.5f),
                topLeft = offset,
                size = size,
                style = Stroke(width = 2f)
            )
        }
        if (drawRowBoxes) {
            for ((idx, row) in rows.withIndex()) {
                val (offset, size) = mapRect(row.left, row.top, row.right, row.bottom)
                val color = if (idx == selectedRowIndex) Color.Red else Color.Blue
                drawRect(
                    color = color.copy(alpha = 0.4f),
                    topLeft = offset,
                    size = size,
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}
