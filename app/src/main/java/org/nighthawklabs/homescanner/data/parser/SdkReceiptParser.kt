package org.nighthawklabs.homescanner.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult
import org.nighthawklabs.homescanner.domain.parser.ReceiptParser
import java.io.File

/**
 * Receipt parser backed by on-device SDK. Catches SDK exceptions and rethrows ReceiptParserException
 * so the coordinator can handle fallback.
 */
class SdkReceiptParser(
    private val engine: ReceiptSdkEngine = ReceiptSdkEngine()
) : ReceiptParser {

    override suspend fun parse(imageFile: File): ParsedReceiptResult = withContext(Dispatchers.Default) {
        runCatching {
            val sdkResult = engine.analyze(imageFile)
            sdkResult.toParsedReceiptResult()
        }.getOrElse { e ->
            throw ReceiptParserException("SDK parse failed: ${e.message}", e)
        }
    }
}

private fun ReceiptSdkResult.toParsedReceiptResult(): ParsedReceiptResult = ParsedReceiptResult(
    merchantName = merchantName,
    purchaseTime = purchaseTime,
    currency = currency,
    items = items.map { it.toParsedItem() },
    subtotal = subtotal,
    tax = tax,
    total = total,
    paidTotal = paidTotal,
    warnings = warnings,
    confidence = confidence,
    parserVendor = vendor,
    parserVersion = version
)

private fun SdkItem.toParsedItem() = ParsedReceiptItem(
    lineId = lineId,
    rawText = rawText,
    itemName = itemName,
    qty = qty,
    unitPrice = unitPrice,
    lineTotal = lineTotal,
    paidPrice = paidPrice,
    confidence = confidence
)

class ReceiptParserException(message: String, cause: Throwable? = null) : Exception(message, cause)
