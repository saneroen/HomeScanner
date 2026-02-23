package org.nighthawklabs.homescanner.data.parser

import kotlinx.serialization.Serializable

/**
 * Debug trace for tuning the parse pipeline.
 * Stores row texts and rejection reasons so you can iterate quickly on parsing rules.
 */
@Serializable
data class ParseDebugTrace(
    val receiptId: String? = null,
    val rowsAfterCluster: List<String> = emptyList(),
    val rowsAfterJoin: List<String> = emptyList(),
    val rowsAfterFilter: List<String> = emptyList(),
    val rowRejections: List<RowRejection> = emptyList(),
    val strictItemCount: Int = 0,
    val fallbackItemCount: Int = 0,
    val relaxedItemCount: Int = 0,
    val usedPath: String = "strict" // "strict" | "fallback" | "relaxed"
) {
    fun toLogString(): String = buildString {
        appendLine("[ParseDebugTrace] receiptId=$receiptId")
        appendLine("  rowsAfterCluster=${rowsAfterCluster.size}")
        appendLine("  rowsAfterJoin=${rowsAfterJoin.size}")
        appendLine("  rowsAfterFilter=${rowsAfterFilter.size}")
        appendLine("  usedPath=$usedPath strict=$strictItemCount fallback=$fallbackItemCount relaxed=$relaxedItemCount")
        if (rowRejections.isNotEmpty()) {
            appendLine("  rejections:")
            rowRejections.take(20).forEach { r ->
                appendLine("    [${r.rowIndex}] ${r.reason}: ${r.rowPreview.take(60)}...")
            }
            if (rowRejections.size > 20) appendLine("    ... and ${rowRejections.size - 20} more")
        }
    }
}

@Serializable
data class RowRejection(
    val rowIndex: Int,
    val rowText: String,
    val reason: String
) {
    val rowPreview: String get() = rowText.take(80).replace("\n", " ")
}
