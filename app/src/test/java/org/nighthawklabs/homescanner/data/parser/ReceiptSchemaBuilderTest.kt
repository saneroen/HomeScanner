package org.nighthawklabs.homescanner.data.parser

import org.junit.Assert.assertTrue
import org.junit.Test
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult

class ReceiptSchemaBuilderTest {

    @Test
    fun emptyItems_includesItemsArrayInJson() {
        val parsed = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = emptyList(),
            subtotal = null,
            tax = null,
            total = null,
            paidTotal = null,
            warnings = emptyList(),
            confidence = 0.5,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val json = ReceiptSchemaBuilder.buildReceiptJson("r1", parsed)
        assertTrue("JSON must contain \"items\": []", json.contains("\"items\"") && json.contains("[]"))
    }

    @Test
    fun twoItems_jsonContainsBothItems() {
        val items = listOf(
            ParsedReceiptItem("li_001", "Milk 2.99", "Milk", 1.0, 2.99, 2.99, 2.99, 0.9),
            ParsedReceiptItem("li_002", "Bread 1.50", "Bread", 1.0, 1.50, 1.50, 1.50, 0.9)
        )
        val parsed = ParsedReceiptResult(
            merchantName = "Store",
            purchaseTime = null,
            currency = "USD",
            items = items,
            subtotal = 4.49,
            tax = 0.0,
            total = 4.49,
            paidTotal = 4.49,
            warnings = emptyList(),
            confidence = 0.9,
            parserVendor = "test",
            parserVersion = "v1"
        )
        val json = ReceiptSchemaBuilder.buildReceiptJson("r1", parsed)
        assertTrue("JSON must contain items", json.contains("\"items\""))
        assertTrue("JSON must contain Milk", json.contains("Milk"))
        assertTrue("JSON must contain Bread", json.contains("Bread"))
    }
}
