package org.nighthawklabs.homescanner.data.parser.layout

import android.graphics.RectF

/**
 * Adaptive centerY clustering. Uses median line height for threshold instead of image fraction.
 * Produces many rows (30-120 typical) instead of merging into 1.
 */
object RowClusterer {

    fun clusterRows(lines: List<OcrLine>, imageHeight: Int): List<OcrRow> {
        val (validLines, unplaced) = lines.partition { it.box.width() > 0 && it.box.height() > 0 }
        if (validLines.isEmpty()) return emptyList()

        val heights = validLines.map { it.box.height() }.sorted()
        val medianH = heights[heights.size / 2]
        val thresholdPx = maxOf(6f, medianH * 0.65f)

        val sorted = validLines.sortedBy { it.centerY }
        val rowClusters = mutableListOf<MutableList<OcrLine>>()
        var current = mutableListOf(sorted[0])
        var rowCenterY = sorted[0].centerY

        for (i in 1 until sorted.size) {
            val line = sorted[i]
            if (kotlin.math.abs(line.centerY - rowCenterY) <= thresholdPx) {
                current.add(line)
                val n = current.size
                rowCenterY = (rowCenterY * (n - 1) + line.centerY) / n
            } else {
                rowClusters.add(current)
                current = mutableListOf(line)
                rowCenterY = line.centerY
            }
        }
        rowClusters.add(current)

        return rowClusters.map { cluster ->
            val sortedInRow = cluster.sortedBy { it.box.left }
            val segments = sortedInRow.flatMap { line ->
                line.tokens.map { token -> Segment(text = token.text, box = token.box) }
            }
            val box = unionRect(sortedInRow.map { it.box })
            OcrRow(textSegments = segments, box = box)
        }
    }

    fun getUnplacedLineTexts(lines: List<OcrLine>): List<String> =
        lines.filter { it.box.width() <= 0 || it.box.height() <= 0 }.map { it.text }

    private fun unionRect(rects: List<RectF>): RectF {
        if (rects.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        var left = rects[0].left
        var top = rects[0].top
        var right = rects[0].right
        var bottom = rects[0].bottom
        for (r in rects.drop(1)) {
            left = minOf(left, r.left)
            top = minOf(top, r.top)
            right = maxOf(right, r.right)
            bottom = maxOf(bottom, r.bottom)
        }
        return RectF(left, top, right, bottom)
    }
}
