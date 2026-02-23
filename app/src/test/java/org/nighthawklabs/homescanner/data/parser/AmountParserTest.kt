package org.nighthawklabs.homescanner.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountParserTest {

    @Test
    fun findAmounts_extractsSimplePrices() {
        val amounts = AmountParser.findAmounts("Milk 2.99 Bread 1.50")
        assertEquals(2, amounts.size)
        assertEquals(2.99, amounts[0].value, 0.001)
        assertEquals(1.50, amounts[1].value, 0.001)
    }

    @Test
    fun findAmounts_handlesCurrencySymbols() {
        val amounts = AmountParser.findAmounts("Total: $20.08")
        assertEquals(1, amounts.size)
        assertEquals(20.08, amounts[0].value, 0.001)
    }

    @Test
    fun findAmounts_handlesCommaSeparators() {
        val amounts = AmountParser.findAmounts("1,234.56")
        assertEquals(1, amounts.size)
        assertEquals(1234.56, amounts[0].value, 0.001)
    }

    @Test
    fun findAmounts_handlesNegativeDiscounts() {
        val amounts = AmountParser.findAmounts("Discount -1.00")
        assertEquals(1, amounts.size)
        assertEquals(-1.00, amounts[0].value, 0.001)
    }

    @Test
    fun parseAmountToken_simple() {
        assertEquals(2.99, AmountParser.parseAmountToken("2.99")!!, 0.001)
        assertEquals(20.08, AmountParser.parseAmountToken("$20.08")!!, 0.001)
    }

    @Test
    fun parseAmountToken_withCommas() {
        assertEquals(1234.56, AmountParser.parseAmountToken("1,234.56")!!, 0.001)
    }

    @Test
    fun parseAmountToken_negative() {
        assertEquals(-1.00, AmountParser.parseAmountToken("(1.00)")!!, 0.001)
    }
}
