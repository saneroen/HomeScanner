package org.nighthawklabs.homescanner.data.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simulated on-device receipt extraction SDK (Microblink/BlinkReceipt style).
 * Replace this with real SDK when available; only SdkReceiptParser needs to change.
 */
class ReceiptSdkEngine {

    suspend fun analyze(imageFile: File): ReceiptSdkResult = withContext(Dispatchers.IO) {
        if (!imageFile.exists()) {
            throw ReceiptSdkException("Image file not found: ${imageFile.absolutePath}")
        }
        delay(300) // Simulate SDK processing time
        val seed = imageFile.absolutePath.hashCode().and(0x7FFFFFFF)
        generateMockResult(seed)
    }

    private fun generateMockResult(seed: Int): ReceiptSdkResult {
        val itemCount = 4 + (seed % 8)
        val items = mutableListOf<SdkItem>()
        var subtotal = 0.0
        for (i in 0 until itemCount) {
            val name = SDK_ITEM_NAMES[(seed + i) % SDK_ITEM_NAMES.size]
            val price = SDK_BASE_PRICES[(seed + i * 5) % SDK_BASE_PRICES.size]
            val qty = if (seed % 7 == 0) 2.0 else 1.0
            val lineTotal = (price * qty * 100).toInt() / 100.0
            subtotal += lineTotal
            items.add(
                SdkItem(
                    lineId = "sdk_li_%03d".format(i + 1),
                    rawText = "$name ${"%.2f".format(price)}",
                    itemName = name.lowercase(),
                    qty = qty,
                    unitPrice = price,
                    lineTotal = lineTotal,
                    paidPrice = lineTotal,
                    confidence = if (i % 3 == 0) 0.82 else 0.94
                )
            )
        }
        subtotal = (subtotal * 100).toInt() / 100.0
        val taxRate = 0.085
        val tax = (subtotal * taxRate * 100).toInt() / 100.0
        val total = ((subtotal + tax) * 100).toInt() / 100.0
        val merchantIdx = (seed / 1000) % SDK_MERCHANT_NAMES.size
        val merchantName = SDK_MERCHANT_NAMES[merchantIdx]
        val purchaseTime = System.currentTimeMillis() - (seed % 14) * 86400000L
        val hasWarning = seed % 4 == 0
        val warnings = if (hasWarning) listOf("Total mismatch by $0.01") else emptyList()
        val confidence = if (hasWarning) 0.72 else 0.91

        return ReceiptSdkResult(
            merchantName = merchantName,
            purchaseTime = purchaseTime,
            currency = "USD",
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = total.toDouble(),
            paidTotal = total.toDouble(),
            confidence = confidence,
            warnings = warnings,
            vendor = "mock_sdk",
            version = "1.0"
        )
    }

    companion object {
        private val SDK_ITEM_NAMES = listOf(
            "ORGANIC BANANAS", "WHOLE MILK 2%", "SOURDOUGH BREAD",
            "FREE RANGE EGGS", "GALA APPLES", "CHICKEN BREAST",
            "BASMATI RICE", "CHEDDAR CHEESE", "GREEK YOGURT",
            "CHERRY TOMATOES", "EXTRA VIRGIN OLIVE OIL", "SPINACH"
        )
        private val SDK_BASE_PRICES = doubleArrayOf(
            2.49, 4.29, 3.99, 5.49, 6.99, 12.99, 8.49, 7.99, 3.29, 4.99, 11.99, 2.79
        )
        private val SDK_MERCHANT_NAMES = listOf(
            "Whole Foods", "Trader Joe's", "Target", "Walmart", "Costco"
        )
    }
}

data class ReceiptSdkResult(
    val merchantName: String,
    val purchaseTime: Long,
    val currency: String,
    val items: List<SdkItem>,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val paidTotal: Double,
    val confidence: Double,
    val warnings: List<String>,
    val vendor: String,
    val version: String
)

data class SdkItem(
    val lineId: String,
    val rawText: String,
    val itemName: String,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val paidPrice: Double,
    val confidence: Double
)

class ReceiptSdkException(message: String) : Exception(message)
