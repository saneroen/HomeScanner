package org.nighthawklabs.homescanner.data.parser.templates

object MerchantTemplateStore {

    private val builtIn = listOf(
        MerchantTemplate(
            merchantKey = "target",
            ignoreRegexes = listOf(
                """target\.com""",
                """circle\s+earn"""
            ),
            subtotalKeywords = listOf("subtotal", "sub total"),
            totalKeywords = listOf("total", "amount due"),
            taxKeywords = listOf("tax", "sales tax"),
            hasTwoAmountColumns = true
        ),
        MerchantTemplate(
            merchantKey = "costco",
            ignoreRegexes = listOf(
                """membership""",
                """warehouse\s+#"""
            ),
            subtotalKeywords = listOf("subtotal", "merchandise total"),
            totalKeywords = listOf("total", "total due"),
            taxKeywords = listOf("tax", "gst", "pst"),
            hasTwoAmountColumns = true
        ),
        MerchantTemplate(
            merchantKey = "trader joe",
            ignoreRegexes = listOf("""trader\s+joe's"""),
            subtotalKeywords = listOf("subtotal"),
            totalKeywords = listOf("total"),
            taxKeywords = listOf("tax"),
            hasTwoAmountColumns = false
        )
    )

    fun getBuiltInTemplates(): List<MerchantTemplate> = builtIn

    fun findTemplate(merchantName: String): MerchantTemplate? =
        MerchantTemplateMatcher.match(merchantName, builtIn)
}
