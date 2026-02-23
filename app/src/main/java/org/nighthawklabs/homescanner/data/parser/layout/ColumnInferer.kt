package org.nighthawklabs.homescanner.data.parser.layout

import org.nighthawklabs.homescanner.data.parser.AmountParser

data class ColumnModel(
    val descriptionRegionXMax: Float,
    val amountColumnXCenters: List<Float>,
    val hasTwoAmountColumns: Boolean
)

object ColumnInferer {

    private val amountPattern = Regex("""[\$\€\£]?\s*\(?-?\d{1,3}(?:,\d{3})*(?:\.\d{2})?\)?""")

    fun inferColumns(rows: List<OcrRow>, imageWidth: Int): ColumnModel {
        val numericPositions = mutableListOf<Float>()
        for (row in rows) {
            for (seg in row.textSegments) {
                if (AmountParser.parseAmountToken(seg.text) != null || amountPattern.containsMatchIn(seg.text)) {
                    numericPositions.add(seg.box.centerX())
                }
            }
        }
        if (numericPositions.isEmpty()) {
            return ColumnModel(
                descriptionRegionXMax = imageWidth * 0.6f,
                amountColumnXCenters = listOf(imageWidth * 0.9f),
                hasTwoAmountColumns = false
            )
        }
        val sorted = numericPositions.sorted()
        val bins = mutableMapOf<Int, Int>()
        val binWidth = (imageWidth / 20).coerceAtLeast(10)
        for (x in sorted) {
            val bin = (x / binWidth).toInt()
            bins[bin] = (bins[bin] ?: 0) + 1
        }
        val topBins = bins.entries.sortedByDescending { it.value }.take(2)
        val amountCenters = topBins
            .sortedBy { it.key }
            .map { (it.key + 0.5f) * binWidth }
            .filter { it > imageWidth * 0.4f }
        val descriptionXMax = if (amountCenters.isNotEmpty()) {
            (amountCenters.minOrNull()!! - imageWidth * 0.05f).coerceAtLeast(0f)
        } else {
            imageWidth * 0.6f
        }
        val hasTwo = amountCenters.size >= 2 &&
            (amountCenters[1] - amountCenters[0]) > imageWidth * 0.1f
        return ColumnModel(
            descriptionRegionXMax = descriptionXMax,
            amountColumnXCenters = amountCenters.ifEmpty { listOf(imageWidth * 0.9f) },
            hasTwoAmountColumns = hasTwo
        )
    }
}
