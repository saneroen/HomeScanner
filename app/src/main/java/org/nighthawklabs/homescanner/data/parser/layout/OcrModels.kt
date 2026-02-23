package org.nighthawklabs.homescanner.data.parser.layout

import android.graphics.RectF

data class OcrToken(
    val text: String,
    val box: RectF,
    val confidence: Float?
)

data class OcrLine(
    val text: String,
    val box: RectF,
    val tokens: List<OcrToken>
) {
    val centerY: Float get() = box.centerY()
    val centerX: Float get() = box.centerX()
}

data class OcrBlock(
    val box: RectF,
    val lines: List<OcrLine>
)

data class OcrLayout(
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrBlock>
) {
    val allLines: List<OcrLine> get() = blocks.flatMap { it.lines }
}

data class Segment(
    val text: String,
    val box: RectF
)

data class OcrRow(
    val textSegments: List<Segment>,
    val box: RectF
) {
    val text: String get() = textSegments.joinToString(" ") { it.text }.trim()
    val centerY: Float get() = box.centerY()
}
