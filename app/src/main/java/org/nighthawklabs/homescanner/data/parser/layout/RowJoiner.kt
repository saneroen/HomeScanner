package org.nighthawklabs.homescanner.data.parser.layout

import org.nighthawklabs.homescanner.data.parser.AmountParser
import android.graphics.RectF

private val hasLettersRegex = Regex("[A-Za-z]{2,}")

data class RowJoinerResult(val rows: List<OcrRow>, val joinsPerformed: Int)

/**
 * Joins description-only rows with next amount-only row.
 * amountOnlyRow: has amounts, letters count < 2.
 * Join when vertical gap is within [-medianH, medianH*1.5].
 */
object RowJoiner {

    fun joinWrappedPrices(rows: List<OcrRow>, columnModel: ColumnModel): List<OcrRow> =
        joinWrappedPricesWithStats(rows, columnModel).rows

    fun joinWrappedPricesWithStats(rows: List<OcrRow>, columnModel: ColumnModel): RowJoinerResult {
        if (rows.size < 2) return RowJoinerResult(rows, 0)
        val medianH = medianRowHeight(rows)
        val result = mutableListOf<OcrRow>()
        var joins = 0
        var i = 0
        while (i < rows.size) {
            val current = rows[i]
            val next = rows.getOrNull(i + 1)
            if (next != null && shouldJoin(current, next, columnModel, medianH)) {
                result.add(mergeRows(current, next))
                joins++
                i += 2
            } else {
                result.add(current)
                i++
            }
        }
        return RowJoinerResult(result, joins)
    }

    private fun medianRowHeight(rows: List<OcrRow>): Float {
        if (rows.isEmpty()) return 20f
        val heights = rows.map { it.box.height() }.sorted()
        return heights[heights.size / 2]
    }

    private fun hasLetters(text: String): Boolean = hasLettersRegex.containsMatchIn(text)

    private fun amountOnlyRow(row: OcrRow): Boolean {
        val amounts = AmountParser.findAmounts(row.text).filter { it.value > 0 }
        if (amounts.isEmpty()) return false
        val letterCount = row.text.count { it.isLetter() }
        return letterCount < 2
    }

    private fun shouldJoin(prev: OcrRow, next: OcrRow, columns: ColumnModel, medianH: Float): Boolean {
        if (!hasLetters(prev.text)) return false
        val prevAmounts = AmountParser.findAmounts(prev.text).filter { it.value > 0 }
        if (prevAmounts.isNotEmpty()) return false
        if (!amountOnlyRow(next)) return false

        val gap = next.box.top - prev.box.bottom
        if (gap < -medianH || gap > medianH * 1.5f) return false
        return true
    }

    private fun mergeRows(a: OcrRow, b: OcrRow): OcrRow {
        val combined = a.textSegments + b.textSegments
        val box = unionRect(combined.map { it.box })
        return OcrRow(textSegments = combined, box = box)
    }

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
