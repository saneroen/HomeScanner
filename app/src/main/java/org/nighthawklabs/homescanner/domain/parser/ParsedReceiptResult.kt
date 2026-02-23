package org.nighthawklabs.homescanner.domain.parser

data class ParsedReceiptResult(
    val merchantName: String?,
    val purchaseTime: Long?,
    val currency: String,
    val items: List<ParsedReceiptItem>,
    val subtotal: Double?,
    val tax: Double?,
    val total: Double?,
    val paidTotal: Double?,
    val warnings: List<String>,
    val confidence: Double,
    val parserVendor: String = "stub",
    val parserVersion: String = "v0",
    val debugTrace: org.nighthawklabs.homescanner.data.parser.ParseDebugTrace? = null,
    val pageParseDebug: org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug? = null
)

data class ParsedReceiptItem(
    val lineId: String,
    val rawText: String,
    val itemName: String,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val paidPrice: Double,
    val confidence: Double
)
