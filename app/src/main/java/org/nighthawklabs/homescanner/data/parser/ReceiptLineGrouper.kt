package org.nighthawklabs.homescanner.data.parser

import com.google.mlkit.vision.text.Text

object ReceiptLineGrouper {

    fun groupLines(text: Text): List<String> =
        text.textBlocks
            .flatMap { it.lines }
            .sortedBy { it.boundingBox?.top ?: 0 }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
}
