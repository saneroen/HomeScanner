package org.nighthawklabs.homescanner.data.parser.layout

import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.text.Text

object OcrLayoutBuilder {

    fun build(text: Text, imageWidth: Int, imageHeight: Int): OcrLayout {
        val blocks = text.textBlocks
            .map { buildBlock(it) }
            .sortedBy { it.box.top }
        return OcrLayout(imageWidth = imageWidth, imageHeight = imageHeight, blocks = blocks)
    }

    private fun buildBlock(block: Text.TextBlock): OcrBlock {
        val lines = block.lines
            .map { buildLine(it) }
            .sortedBy { it.box.top }
        val box = unionRect(lines.map { it.box })
        return OcrBlock(box = box, lines = lines)
    }

    private fun buildLine(line: Text.Line): OcrLine {
        val tokens = line.elements.map { buildToken(it) }
        val box = line.boundingBox?.toRectF() ?: unionRect(tokens.map { it.box })
        val normalizedText = OcrNormalizer.normalizeText(line.text)
        return OcrLine(text = normalizedText, box = box, tokens = tokens)
    }

    private fun buildToken(element: Text.Element): OcrToken {
        val box = element.boundingBox?.toRectF() ?: RectF(0f, 0f, 0f, 0f)
        return OcrToken(text = element.text, box = box, confidence = null)
    }

    private fun Rect.toRectF(): RectF = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

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
