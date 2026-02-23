package org.nighthawklabs.homescanner.data.parser.debug

import kotlinx.serialization.Serializable

@Serializable
data class PageParseDebug(
    val receiptId: String,
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<DebugLine> = emptyList(),
    val rows: List<DebugRow> = emptyList(),
    val itemCandidatesStrict: List<DebugCandidate> = emptyList(),
    val itemCandidatesRelaxed: List<DebugCandidate> = emptyList(),
    val rejectionSummary: Map<String, Int> = emptyMap(),
    val sampleOcrText: String = "",
    val rowsCountAfterCluster: Int = 0,
    val rowsCountAfterJoin: Int = 0,
    val joinsPerformed: Int = 0,
    val summaryRowsCount: Int = 0,
    val itemRowsCount: Int = 0,
    val unplacedLines: List<String> = emptyList()
)

@Serializable
data class DebugLine(
    val text: String,
    val left: Float?,
    val top: Float?,
    val right: Float?,
    val bottom: Float?
)

@Serializable
data class DebugRow(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val amountsFound: List<Double> = emptyList(),
    val isSummary: Boolean = false,
    val rejectedReasonStrict: String? = null,
    val rejectedReasonRelaxed: String? = null,
    val joined: Boolean = false
)

@Serializable
data class DebugCandidate(
    val itemName: String,
    val rawText: String,
    val lineTotal: Double,
    val confidence: Double,
    val source: String = "strict" // "strict" | "relaxed"
)
