package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.data.parser.layout.ColumnModel
import org.nighthawklabs.homescanner.data.parser.layout.OcrRow

data class SummaryCandidates(
    val subtotal: Double? = null,
    val tax: Double? = null,
    val total: Double? = null,
    val paidTotal: Double? = null,
    val discount: Double? = null,
    val fee: Double? = null,
    val subtotalConfidence: Float = 0f,
    val taxConfidence: Float = 0f,
    val totalConfidence: Float = 0f
)

object ReceiptSummaryExtractor {

    private val subtotalKeywords = Regex("""\b(sub\s*total|subtotal|sub-total|sub\s*ttl)\b""", RegexOption.IGNORE_CASE)
    private val taxKeywords = Regex("""\b(tax|vat|gst|sales\s*tax)\b""", RegexOption.IGNORE_CASE)
    private val totalKeywords = Regex("""\b(total|amount\s*due|balance\s*due)\b""", RegexOption.IGNORE_CASE)
    private val paidKeywords = Regex("""\b(paid|tender|cash|visa|mastercard|amex|change)\b""", RegexOption.IGNORE_CASE)
    private val discountKeywords = Regex("""(discount|savings|coupon|promo)""", RegexOption.IGNORE_CASE)
    private val feeKeywords = Regex("""(fee|bag|service)""", RegexOption.IGNORE_CASE)

    fun extract(rows: List<OcrRow>, columnModel: ColumnModel): SummaryCandidates {
        var subtotal: Double? = null
        var tax: Double? = null
        var total: Double? = null
        var paidTotal: Double? = null
        var discount: Double? = null
        var fee: Double? = null
        var subConf = 0f
        var taxConf = 0f
        var totalConf = 0f

        val amountColumnCenter = columnModel.amountColumnXCenters.lastOrNull() ?: 0f
        val sortedRows = rows.sortedByDescending { it.centerY }

        for (row in sortedRows.reversed()) {
            val amountPart = row.textSegments
                .filter { kotlin.math.abs(it.box.centerX() - amountColumnCenter) < 200 }
                .joinToString(" ") { it.text }
            val amounts = AmountParser.findAmounts(row.text)
            val amountVal = amounts.lastOrNull()?.value
            if (amountVal == null) continue

            when {
                subtotalKeywords.containsMatchIn(row.text) && !totalKeywords.containsMatchIn(row.text) -> {
                    subtotal = amountVal
                    subConf = 0.9f
                }
                taxKeywords.containsMatchIn(row.text) -> {
                    tax = amountVal
                    taxConf = 0.9f
                }
                totalKeywords.containsMatchIn(row.text) && !row.text.lowercase().contains("sub") -> {
                    total = amountVal
                    totalConf = 0.9f
                }
                paidKeywords.containsMatchIn(row.text) -> {
                    paidTotal = amountVal
                }
                discountKeywords.containsMatchIn(row.text) -> {
                    discount = amountVal
                }
                feeKeywords.containsMatchIn(row.text) -> {
                    fee = amountVal
                }
            }
        }

        if (total == null) {
            val lastAmountRows = sortedRows.takeLast(5)
            for (row in lastAmountRows.reversed()) {
                if (!summaryKeywords(row.text)) {
                    val amt = AmountParser.findAmounts(row.text).lastOrNull()?.value
                    if (amt != null && amt > 0) {
                        total = amt
                        totalConf = 0.7f
                        break
                    }
                }
            }
        }

        return SummaryCandidates(
            subtotal = subtotal,
            tax = tax,
            total = total,
            paidTotal = paidTotal,
            discount = discount,
            fee = fee,
            subtotalConfidence = subConf,
            taxConfidence = taxConf,
            totalConfidence = totalConf
        )
    }

    fun summaryKeywords(text: String): Boolean =
        subtotalKeywords.containsMatchIn(text) ||
            taxKeywords.containsMatchIn(text) ||
            totalKeywords.containsMatchIn(text) ||
            paidKeywords.containsMatchIn(text)
}
