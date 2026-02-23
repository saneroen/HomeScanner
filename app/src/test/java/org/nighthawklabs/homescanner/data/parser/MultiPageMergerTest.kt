package org.nighthawklabs.homescanner.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult

class MultiPageMergerTest {

    @Test
    fun twoPagesWithItems_mergedSizeEqualsSum() {
        val page0 = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = listOf(
                ParsedReceiptItem("li_001", "Milk 2.99", "Milk", 1.0, 2.99, 2.99, 2.99, 0.9),
                ParsedReceiptItem("li_002", "Bread 1.50", "Bread", 1.0, 1.50, 1.50, 1.50, 0.9)
            ),
            subtotal = 4.49,
            tax = null,
            total = 4.49,
            paidTotal = 4.49,
            warnings = emptyList(),
            confidence = 0.9,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val page1 = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = listOf(
                ParsedReceiptItem("li_001", "Eggs 3.00", "Eggs", 1.0, 3.00, 3.00, 3.00, 0.9),
                ParsedReceiptItem("li_002", "Butter 4.50", "Butter", 1.0, 4.50, 4.50, 4.50, 0.9)
            ),
            subtotal = 7.50,
            tax = null,
            total = 7.50,
            paidTotal = 7.50,
            warnings = emptyList(),
            confidence = 0.9,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val merged = MultiPageMerger.merge(
            listOf(
                MultiPageMerger.PageResult(0, page0),
                MultiPageMerger.PageResult(1, page1)
            ),
            null
        )
        assertEquals(4, merged.items.size)
    }

    @Test
    fun lineIdsAreUniqueAcrossPages() {
        val page0 = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = listOf(ParsedReceiptItem("li_001", "Milk 2.99", "Milk", 1.0, 2.99, 2.99, 2.99, 0.9)),
            subtotal = null,
            tax = null,
            total = null,
            paidTotal = null,
            warnings = emptyList(),
            confidence = 0.9,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val page1 = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = listOf(ParsedReceiptItem("li_001", "Bread 1.50", "Bread", 1.0, 1.50, 1.50, 1.50, 0.9)),
            subtotal = null,
            tax = null,
            total = null,
            paidTotal = null,
            warnings = emptyList(),
            confidence = 0.9,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val merged = MultiPageMerger.merge(
            listOf(
                MultiPageMerger.PageResult(0, page0),
                MultiPageMerger.PageResult(1, page1)
            ),
            null
        )
        val lineIds = merged.items.map { it.lineId }.toSet()
        assertEquals(2, lineIds.size)
        assertTrue(merged.items.any { it.lineId.startsWith("p0_") })
        assertTrue(merged.items.any { it.lineId.startsWith("p1_") })
    }
}
