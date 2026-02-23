package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.data.parser.layout.ColumnModel
import org.nighthawklabs.homescanner.data.parser.layout.OcrRow
import org.nighthawklabs.homescanner.data.parser.layout.Segment
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem

object ReceiptItemExtractor {

    const val REJECT_NO_BBOX = "NO_BBOX"
    const val REJECT_NO_AMOUNT = "NO_AMOUNT"
    const val REJECT_SUMMARY_ROW = "SUMMARY_ROW"
    const val REJECT_TOO_SHORT = "TOO_SHORT"
    const val REJECT_NO_LETTERS = "NO_LETTERS"
    const val REJECT_IGNORED_BY_REGEX = "IGNORED_BY_REGEX"
    const val REJECT_STRICT_PARSE_FAILED = "OTHER"

    private val summaryKeywords = Regex(
        """(subtotal|sub\s*total|sub-total|sub\s*ttl|tax|vat|gst|sales\s*tax|
        total|amount|balance|balance\s*due|paid|amount\s*paid|card|visa|mastercard|amex|discount|
        savings|coupon|promo|fee|bag|service|change|cash|tender|tip|gratuity|labor|installation)""".trimMargin().replace("\n", ""),
        RegexOption.IGNORE_CASE
    )
    private val qtyPattern = Regex("""(\d+(?:\.\d+)?)\s*[xX@]\s*(\d+\.\d{2})|^(\d+)\s+|\s+(\d+)\s*[xX@]|(\d+(?:\.\d+)?)\s*(lb|oz|kg)""", RegexOption.IGNORE_CASE)
    private val numericOnly = Regex("""^[\d\s\.\$\,\-]+$""")

    fun isSummaryRow(text: String): Boolean = summaryKeywords.containsMatchIn(text)

    data class ExtractResult(
        val items: List<ParsedReceiptItem>,
        val usedFallback: Boolean,
        val usedRelaxed: Boolean = false,
        val rejections: List<RowRejection> = emptyList(),
        val strictCount: Int = 0,
        val fallbackCount: Int = 0,
        val relaxedCount: Int = 0
    )

    fun extractItems(
        rows: List<OcrRow>,
        columnModel: ColumnModel,
        ignoreRegexes: List<Regex> = emptyList(),
        itemAliasMap: Map<String, String> = emptyMap(),
        buildTrace: Boolean = false
    ): ExtractResult {
        val rejections = if (buildTrace) mutableListOf<RowRejection>() else null
        val strictItems = extractItemsStrict(rows, columnModel, ignoreRegexes, itemAliasMap, rejections)
        if (strictItems.isNotEmpty()) return ExtractResult(
            items = strictItems,
            usedFallback = false,
            rejections = rejections.orEmpty(),
            strictCount = strictItems.size
        )
        val fallbackItems = extractItemsFallback(rows, ignoreRegexes, itemAliasMap, rejections)
        if (fallbackItems.isNotEmpty()) return ExtractResult(
            items = fallbackItems,
            usedFallback = true,
            rejections = rejections.orEmpty(),
            fallbackCount = fallbackItems.size
        )
        val relaxedItems = extractItemsRelaxed(rows, ignoreRegexes, itemAliasMap)
        return ExtractResult(
            items = relaxedItems,
            usedFallback = true,
            usedRelaxed = true,
            rejections = rejections.orEmpty(),
            relaxedCount = relaxedItems.size
        )
    }

    private fun extractItemsStrict(
        rows: List<OcrRow>,
        columnModel: ColumnModel,
        ignoreRegexes: List<Regex>,
        aliasMap: Map<String, String>,
        rejections: MutableList<RowRejection>?
    ): List<ParsedReceiptItem> {
        val result = mutableListOf<ParsedReceiptItem>()
        for ((index, row) in rows.withIndex()) {
            if (ignoreRegexes.any { it.containsMatchIn(row.text) }) {
                rejections?.add(RowRejection(index, row.text, REJECT_IGNORED_BY_REGEX))
                continue
            }
            if (isSummaryRow(row.text)) {
                rejections?.add(RowRejection(index, row.text, REJECT_SUMMARY_ROW))
                continue
            }
            val item = parseItemRowStrict(row, columnModel, index, aliasMap)
            if (item == null) {
                rejections?.add(RowRejection(index, row.text, REJECT_STRICT_PARSE_FAILED))
                continue
            }
            result.add(item)
        }
        return result
    }

    private fun extractItemsFallback(
        rows: List<OcrRow>,
        ignoreRegexes: List<Regex>,
        aliasMap: Map<String, String>,
        rejections: MutableList<RowRejection>?
    ): List<ParsedReceiptItem> {
        val result = mutableListOf<ParsedReceiptItem>()
        var lineIdCounter = 0
        for ((index, row) in rows.withIndex()) {
            if (ignoreRegexes.any { it.containsMatchIn(row.text) }) continue
            if (row.text.length < 80 && isSummaryRow(row.text)) continue
            val subLines = splitMergedRow(row.text)
            for (sub in subLines) {
                if (sub.length < 3) continue
                if (isSummaryRow(sub)) continue
                if (!sub.any { it.isLetter() }) continue
                val amounts = AmountParser.findAmounts(sub).map { it.value }.filter { it > 0 }
                if (amounts.isEmpty()) continue
                val lineTotal = amounts.last()
                val itemName = trimRightPrices(sub).trim().replace(Regex("""\s+"""), " ").ifEmpty { "Item" }
                if (itemName.length < 2) continue
                val lineId = "li_%03d".format((lineIdCounter * 31 + sub.hashCode()) and 0x7FFFFFFF % 10000)
                lineIdCounter++
                val finalName = applyAlias(itemName, aliasMap)
                result.add(ParsedReceiptItem(
                lineId = lineId,
                rawText = sub,
                itemName = finalName,
                qty = 1.0,
                unitPrice = lineTotal,
                lineTotal = lineTotal,
                paidPrice = lineTotal,
                confidence = 0.65
            ))
            }
        }
        return result
    }

    private fun extractItemsRelaxed(
        rows: List<OcrRow>,
        ignoreRegexes: List<Regex>,
        aliasMap: Map<String, String>
    ): List<ParsedReceiptItem> {
        val result = mutableListOf<ParsedReceiptItem>()
        var lineIdCounter = 0
        for (row in rows) {
            if (ignoreRegexes.any { it.containsMatchIn(row.text) }) continue
            val subLines = splitMergedRow(row.text)
            for (sub in subLines) {
                if (sub.length < 2) continue
                if (!sub.any { it.isLetter() }) continue
                val amounts = AmountParser.findAmounts(sub).map { it.value }.filter { it > 0 && it < 10000 }
                if (amounts.isEmpty()) continue
                val lineTotal = amounts.last()
                val itemName = trimRightPrices(sub).trim().replace(Regex("""\s+"""), " ").ifEmpty { "Item" }
                val finalName = applyAlias(itemName, aliasMap)
                val lineId = "li_%03d".format((lineIdCounter * 31 + sub.hashCode()) and 0x7FFFFFFF % 10000)
                lineIdCounter++
                result.add(ParsedReceiptItem(
                    lineId = lineId,
                    rawText = sub,
                    itemName = finalName,
                    qty = 1.0,
                    unitPrice = lineTotal,
                    lineTotal = lineTotal,
                    paidPrice = lineTotal,
                    confidence = 0.5
                ))
            }
        }
        return result
    }

    private fun applyAlias(name: String, aliasMap: Map<String, String>): String {
        if (aliasMap.isEmpty()) return name
        val key = name.lowercase().trim().replace(Regex("""\s+"""), " ")
        return aliasMap[key] ?: name
    }

    private fun splitMergedRow(text: String): List<String> {
        if (text.length < 80) return listOf(text)
        val amountMatches = AmountParser.findAmounts(text).filter { it.value > 0 && it.value < 10000 }
        if (amountMatches.size < 2) return listOf(text)
        val parts = mutableListOf<String>()
        var start = 0
        for (am in amountMatches) {
            val before = text.substring(start, am.spanStart).trim()
            if (before.length >= 3 && before.any { it.isLetter() }) {
                val segment = text.substring(start, am.spanEnd).trim()
                if (segment.length >= 5 && !numericOnly.matches(segment)) parts.add(segment)
            }
            start = am.spanEnd
        }
        if (parts.isEmpty()) return listOf(text)
        return parts
    }

    private fun trimRightPrices(text: String): String {
        var s = text
        var prev = ""
        while (s != prev) {
            prev = s
            s = s.replace(Regex("""\s*[\$\€\£]?\s*\(?-?\d{1,3}(?:,\d{3})*(?:\.\d{2})?\)?\s*$"""), "").trim()
        }
        return s
    }

    private fun parseItemRowStrict(row: OcrRow, columns: ColumnModel, index: Int, aliasMap: Map<String, String>): ParsedReceiptItem? {
        val descSegments = row.textSegments.filter { it.box.centerX() <= columns.descriptionRegionXMax }
        val amountSegments = row.textSegments.filter { it.box.centerX() > columns.descriptionRegionXMax }
        val fullText = row.text
        if (fullText.length < 3) return null
        if (numericOnly.matches(fullText.trim())) return null

        val amounts = amountSegments.flatMap { AmountParser.findAmounts(it.text) }
        if (amounts.isEmpty()) return null
        val amountValues = amounts.map { it.value }
        if (amountValues.any { it == 0.0 } && amountValues.size == 1) return null

        val lineTotal = amountValues.last()
        val unitPrice = when {
            amountValues.size >= 2 && columns.hasTwoAmountColumns -> amountValues[amountValues.size - 2]
            amountValues.size >= 2 -> amountValues[amountValues.size - 2]
            else -> lineTotal
        }
        val qty = detectQty(fullText)
        val effectiveUnitPrice = if (qty > 1.0 && amountValues.size == 1) lineTotal / qty else unitPrice
        val rawName = buildItemName(descSegments, fullText)
        if (rawName.length < 2) return null
        val itemName = applyAlias(rawName, aliasMap)

        val lineId = "li_%03d".format((index * 31 + fullText.hashCode()) and 0x7FFFFFFF % 1000)
        var confidence = 0.9
        if (amountValues.size == 1 && qty > 1.0) confidence -= 0.05
        if (!fullText.replace(Regex("""[\d\.\$\s]"""), "").any { it.isLetter() }) confidence -= 0.1

        return ParsedReceiptItem(
            lineId = lineId,
            rawText = fullText,
            itemName = itemName,
            qty = qty,
            unitPrice = effectiveUnitPrice,
            lineTotal = lineTotal,
            paidPrice = lineTotal,
            confidence = confidence.coerceIn(0.0, 1.0)
        )
    }

    private fun detectQty(text: String): Double {
        val match = qtyPattern.find(text) ?: return 1.0
        val g1 = match.groupValues.getOrNull(1)?.toIntOrNull()
        val g2 = match.groupValues.getOrNull(2)?.toIntOrNull()
        val g3 = match.groupValues.getOrNull(3)?.toIntOrNull()
        return (g1 ?: g2 ?: g3)?.toDouble() ?: 1.0
    }

    private fun buildItemName(descSegments: List<Segment>, fullText: String): String {
        val descText = descSegments.joinToString(" ") { it.text }.trim()
        val base = if (descText.isNotEmpty()) descText else fullText
        return base
            .replace(Regex("""\d+\.\d{2}"""), "")
            .replace(Regex("""\d+\s*[xX@]"""), "")
            .replace(Regex("""[\$\€\£]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(100)
            .ifEmpty { "Item" }
    }
}
