package org.nighthawklabs.homescanner.data.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptSummaryExtractorTest {

    @Test
    fun summaryKeywords_detectsSubtotal() {
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Subtotal: 10.00"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("SUB TOTAL 10.00"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("sub-total 10.00"))
    }

    @Test
    fun summaryKeywords_detectsTax() {
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Tax 1.50"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("VAT 2.00"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Sales Tax 1.50"))
    }

    @Test
    fun summaryKeywords_detectsTotal() {
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Total 11.50"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Amount Due 11.50"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Balance Due 11.50"))
    }

    @Test
    fun summaryKeywords_detectsPaid() {
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("Amount Paid 11.50"))
        assertTrue(ReceiptSummaryExtractor.summaryKeywords("VISA 11.50"))
    }

    @Test
    fun summaryKeywords_rejectsItemLine() {
        assertFalse(ReceiptSummaryExtractor.summaryKeywords("Milk 2.99"))
        assertFalse(ReceiptSummaryExtractor.summaryKeywords("Organic Bananas 1.99"))
    }
}
