package org.nighthawklabs.homescanner.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptHeuristicParserItemTest {

    @Test
    fun extractsItemsExcludesSubtotal() {
        val lines = listOf(
            "CARROTS 1.99",
            "MILK 2.49",
            "SUBTOTAL 4.48"
        )
        val result = ReceiptHeuristicParser.parse(lines)
        assertEquals(2, result.items.size)
        assertFalse(result.items.any { it.itemName.contains("SUBTOTAL", ignoreCase = true) })
    }

}
