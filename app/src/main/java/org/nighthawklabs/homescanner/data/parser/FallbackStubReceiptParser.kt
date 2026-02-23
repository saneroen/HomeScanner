package org.nighthawklabs.homescanner.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult
import org.nighthawklabs.homescanner.domain.parser.ReceiptParser
import java.io.File

/**
 * Fallback parser when SDK fails. Generates deterministic placeholder data from image file path.
 */
class FallbackStubReceiptParser : ReceiptParser {

    override suspend fun parse(imageFile: File): ParsedReceiptResult = withContext(Dispatchers.Default) {
        val seed = imageFile.absolutePath.hashCode().and(0x7FFFFFFF)
        generateStubResult(seed)
    }

    private fun generateStubResult(seed: Int): ParsedReceiptResult {
        val itemCount = 4 + (seed % 7)
        val items = mutableListOf<ParsedReceiptItem>()
        var subtotal = 0.0
        for (i in 0 until itemCount) {
            val name = ITEM_NAMES[(seed + i) % ITEM_NAMES.size]
            val price = BASE_PRICES[(seed + i * 3) % BASE_PRICES.size]
            val qty = if (seed % 5 == 0) 2.0 else 1.0
            val lineTotal = (price * qty * 100).toInt() / 100.0
            subtotal += lineTotal
            items.add(
                ParsedReceiptItem(
                    lineId = "stub_li_%03d".format(i + 1),
                    rawText = "$name ${"%.2f".format(price)}",
                    itemName = name.lowercase(),
                    qty = qty,
                    unitPrice = price,
                    lineTotal = lineTotal,
                    paidPrice = lineTotal,
                    confidence = if (i % 4 == 0) 0.85 else 0.95
                )
            )
        }
        subtotal = (subtotal * 100).toInt() / 100.0
        val taxRate = 0.09
        val tax = (subtotal * taxRate * 100).toInt() / 100.0
        val total = (subtotal + tax) * 100 / 100.0
        val merchantIdx = seed % MERCHANT_NAMES.size
        val merchantName = MERCHANT_NAMES[merchantIdx]
        val purchaseTime = System.currentTimeMillis() - (seed % 7) * 86400000L
        val hasWarning = seed % 3 == 0
        val warnings = if (hasWarning) listOf("Total mismatch by $0.02") else emptyList()
        val confidence = if (hasWarning) 0.75 else 0.88

        return ParsedReceiptResult(
            merchantName = merchantName,
            purchaseTime = purchaseTime,
            currency = "USD",
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = total,
            paidTotal = total,
            warnings = warnings,
            confidence = confidence,
            parserVendor = "stub",
            parserVersion = "v0"
        )
    }

    companion object {
        private val ITEM_NAMES = listOf(
            "CARROTS", "MILK", "BREAD", "EGGS", "APPLES",
            "CHICKEN", "RICE", "CHEESE", "YOGURT", "TOMATOES"
        )
        private val BASE_PRICES = doubleArrayOf(
            1.99, 3.49, 2.99, 4.29, 5.99, 8.49, 6.99, 7.29, 2.49, 3.99
        )
        private val MERCHANT_NAMES = listOf(
            "SuperMart", "QuickStop", "Green Grocers", "City Market", "Receipt"
        )
    }
}
