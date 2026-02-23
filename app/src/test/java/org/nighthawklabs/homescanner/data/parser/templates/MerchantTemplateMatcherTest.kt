package org.nighthawklabs.homescanner.data.parser.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantTemplateMatcherTest {

    @Test
    fun match_exactMatch() {
        val templates = listOf(
            MerchantTemplate(merchantKey = "target"),
            MerchantTemplate(merchantKey = "costco")
        )
        val result = MerchantTemplateMatcher.match("Target", templates)
        assertEquals("target", result?.merchantKey)
    }

    @Test
    fun match_containsMatch() {
        val templates = listOf(
            MerchantTemplate(merchantKey = "target"),
            MerchantTemplate(merchantKey = "costco")
        )
        val result = MerchantTemplateMatcher.match("Target Store #1234", templates)
        assertEquals("target", result?.merchantKey)
    }

    @Test
    fun match_tokenOverlap() {
        val templates = listOf(
            MerchantTemplate(merchantKey = "trader joe")
        )
        val result = MerchantTemplateMatcher.match("Trader Joe's Market", templates)
        assertEquals("trader joe", result?.merchantKey)
    }

    @Test
    fun match_returnsNullForUnknown() {
        val templates = listOf(
            MerchantTemplate(merchantKey = "target")
        )
        val result = MerchantTemplateMatcher.match("Unknown Store XYZ", templates)
        assertNull(result)
    }
}
