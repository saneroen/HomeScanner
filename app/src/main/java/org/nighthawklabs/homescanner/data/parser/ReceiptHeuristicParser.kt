package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult

object ReceiptHeuristicParser {

    private val SUBTOTAL_KEYWORDS = Regex("""(subtotal|sub\s*total|sub-total)""", RegexOption.IGNORE_CASE)
    private val TAX_KEYWORDS = Regex("""(tax|vat|gst)""", RegexOption.IGNORE_CASE)
    private val TOTAL_KEYWORDS = Regex("""(^|[^\w])(total)([^\w]|$)""", RegexOption.IGNORE_CASE)
    private val PAID_KEYWORDS = Regex("""(paid|amount\s*paid|amt\s*paid)""", RegexOption.IGNORE_CASE)
    private val QTY_PATTERN = Regex("""(\d+)\s*[xX@]|^(\d+)\s+""")
    private val NUMERIC_ONLY = Regex("""^[\d\s\.\$\,]+$""")

    fun parse(lines: List<String>): ParsedReceiptResult {
        var merchantName: String? = null
        var purchaseTime: Long? = null
        var subtotal: Double? = null
        var tax: Double? = null
        var total: Double? = null
        var paidTotal: Double? = null
        val items = mutableListOf<ParsedReceiptItem>()
        val allWarnings = mutableListOf<String>()

        var merchantFound = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (!merchantFound && !isNumericOnly(trimmed) && !isSummaryLine(trimmed)) {
                val amounts = AmountExtractor.extractAmounts(trimmed)
                if (amounts.isEmpty() && trimmed.length in 2..60) {
                    merchantName = trimmed.takeIf { it.length > 1 }
                    merchantFound = true
                    continue
                }
            }

            purchaseTime = purchaseTime ?: AmountExtractor.extractDate(trimmed)

            when {
                SUBTOTAL_KEYWORDS.containsMatchIn(trimmed) -> {
                    subtotal = AmountExtractor.extractAmounts(trimmed).lastOrNull()
                }
                TAX_KEYWORDS.containsMatchIn(trimmed) && !SUBTOTAL_KEYWORDS.containsMatchIn(trimmed) -> {
                    tax = AmountExtractor.extractAmounts(trimmed).lastOrNull()
                }
                TOTAL_KEYWORDS.containsMatchIn(trimmed) && !SUBTOTAL_KEYWORDS.containsMatchIn(trimmed) -> {
                    total = AmountExtractor.extractAmounts(trimmed).lastOrNull()
                }
                PAID_KEYWORDS.containsMatchIn(trimmed) -> {
                    paidTotal = AmountExtractor.extractAmounts(trimmed).lastOrNull()
                }
                else -> {
                    val parsed = parseItemLine(trimmed)
                    if (parsed != null) {
                        items.add(parsed)
                    }
                }
            }
        }

        merchantName = merchantName ?: lines.firstOrNull { it.trim().isNotEmpty() && !isNumericOnly(it.trim()) }?.trim() ?: "Receipt"

        val (reconWarnings, confidenceModifier) = ReceiptTotalsReconciler.reconcile(
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = total
        )
        allWarnings.addAll(reconWarnings)

        var confidence = 0.85
        if (total == null) confidence -= 0.2
        if (subtotal == null) confidence -= 0.1
        if (items.isEmpty()) confidence -= 0.5
        confidence += confidenceModifier
        confidence = confidence.coerceIn(0.0, 1.0)

        val effectiveTotal = total ?: subtotal?.let { s -> tax?.let { s + it } ?: s }
        val effectivePaidTotal = paidTotal ?: effectiveTotal

        return ParsedReceiptResult(
            merchantName = merchantName,
            purchaseTime = purchaseTime,
            currency = "USD",
            items = items,
            subtotal = subtotal,
            tax = tax,
            total = total ?: effectiveTotal,
            paidTotal = effectivePaidTotal,
            warnings = allWarnings,
            confidence = confidence,
            parserVendor = "mlkit",
            parserVersion = "v1"
        )
    }

    private fun isSummaryLine(line: String): Boolean =
        SUBTOTAL_KEYWORDS.containsMatchIn(line) ||
            TAX_KEYWORDS.containsMatchIn(line) ||
            TOTAL_KEYWORDS.containsMatchIn(line) ||
            PAID_KEYWORDS.containsMatchIn(line)

    private fun isNumericOnly(line: String): Boolean = NUMERIC_ONLY.matches(line)

    private fun parseItemLine(line: String): ParsedReceiptItem? {
        val amounts = AmountExtractor.extractAmounts(line)
        if (amounts.isEmpty()) return null
        if (isSummaryLine(line)) return null
        if (line.length < 3) return null
        if (isNumericOnly(line)) return null

        val lineTotal = amounts.last()
        val qtyMatch = QTY_PATTERN.find(line)
        val qty = when {
            qtyMatch != null -> {
                (qtyMatch.groupValues.getOrNull(1)?.toIntOrNull()
                    ?: qtyMatch.groupValues.getOrNull(2)?.toIntOrNull())?.toDouble() ?: 1.0
            }
            else -> 1.0
        }

        val unitPrice = when {
            amounts.size >= 2 -> amounts[amounts.size - 2]
            else -> lineTotal / qty
        }
        val paidPrice = lineTotal

        val namePart = line
            .replace(Regex("""\d+\.\d{2}"""), " ")
            .replace(Regex("""\d+\s*[xX@]"""), " ")
            .replace(Regex("""[\$\€\£]"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
            .takeIf { it.length > 1 } ?: "Item"

        val lineId = "li_%03d".format((line.hashCode() and 0x7FFFFFFF) % 1000)
        val itemName = namePart.ifEmpty { "Item" }

        return ParsedReceiptItem(
            lineId = lineId,
            rawText = line,
            itemName = itemName,
            qty = qty,
            unitPrice = unitPrice,
            lineTotal = lineTotal,
            paidPrice = paidPrice,
            confidence = 0.9
        )
    }
}
