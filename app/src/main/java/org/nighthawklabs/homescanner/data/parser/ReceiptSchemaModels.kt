package org.nighthawklabs.homescanner.data.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReceiptParsedSchema(
    @SerialName("receipt_id") val receiptId: String = "",
    val currency: String = "USD",
    val merchant: ReceiptMerchant = ReceiptMerchant(),
    @SerialName("purchase_time") val purchaseTime: String? = null,
    val items: List<ReceiptItemSchema> = emptyList(),
    val summary: ReceiptSummarySchema? = null,
    val warnings: List<String> = emptyList(),
    val parser: ReceiptParserInfo? = null,
    @SerialName("debug_trace") val debugTrace: ParseDebugTrace? = null
)

@Serializable
data class ReceiptMerchant(
    val name: String = "Receipt"
)

@Serializable
data class ReceiptItemSchema(
    @SerialName("line_id") val lineId: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("item_name") val itemName: String,
    val qty: Double = 1.0,
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    @SerialName("line_total") val lineTotal: Double = 0.0,
    @SerialName("paid_price") val paidPrice: Double = 0.0,
    val confidence: Double = 1.0
)

@Serializable
data class ReceiptSummarySchema(
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("paid_total") val paidTotal: Double = 0.0
)

@Serializable
data class ReceiptParserInfo(
    val vendor: String = "stub",
    val version: String = "v0",
    val confidence: Double = 0.0
)
