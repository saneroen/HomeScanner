package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.domain.model.MerchantLearning
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult

/**
 * Merges results from multiple receipt pages into a single ParsedReceiptResult.
 */
object MultiPageMerger {

    data class PageResult(
        val pageIndex: Int,
        val result: ParsedReceiptResult
    )

    fun merge(
        pageResults: List<PageResult>,
        learning: MerchantLearning?
    ): ParsedReceiptResult {
        if (pageResults.isEmpty()) {
            return ParsedReceiptResult(
                merchantName = null,
                purchaseTime = null,
                currency = "USD",
                items = emptyList(),
                subtotal = null,
                tax = null,
                total = null,
                paidTotal = null,
                warnings = listOf("No pages parsed"),
                confidence = 0.0,
                parserVendor = "mlkit",
                parserVersion = "v1.1"
            )
        }
        val ignoreRegexes = learning?.ignoreRegexes?.mapNotNull { runCatching { Regex(it) }.getOrNull() } ?: emptyList()
        val aliasMap = learning?.aliasMap ?: emptyMap()
        val normalizedKey: (String) -> String = { s -> s.lowercase().trim().replace(Regex("""\s+"""), " ") }

        val merchant = pageResults
            .firstOrNull { it.result.merchantName != null && it.result.merchantName!!.length in 3..60 }
            ?.result?.merchantName
            ?: pageResults.first().result.merchantName
            ?: "Receipt"

        val purchaseTime = pageResults.firstNotNullOfOrNull { it.result.purchaseTime }

        val currency = pageResults.firstOrNull { it.result.currency != "USD" }?.result?.currency
            ?: pageResults.first().result.currency

        val allItems = pageResults.flatMapIndexed { pageIdx, pr ->
            pr.result.items
                .filter { item -> !ignoreRegexes.any { it.containsMatchIn(item.rawText.trim()) } }
                .mapIndexed { rowIdx, item ->
                    val mappedName = aliasMap[normalizedKey(item.rawText)] ?: item.itemName
                    val lineId = "p${pageIdx}_${rowIdx}_${item.lineId}"
                    item.copy(itemName = mappedName, lineId = lineId)
                }
        }

        val dedupedItems = dedupeItems(allItems)
        val lastPage = pageResults.last().result
        val subtotal = lastPage.subtotal ?: pageResults.firstNotNullOfOrNull { it.result.subtotal }
        val tax = lastPage.tax ?: pageResults.firstNotNullOfOrNull { it.result.tax }
        val total = lastPage.total ?: pageResults.firstNotNullOfOrNull { it.result.total }
        val paidTotal = lastPage.paidTotal ?: total

        val reconResult = ReceiptTotalsReconciler.reconcile(
            dedupedItems,
            SummaryCandidates(
                subtotal = subtotal,
                tax = tax,
                total = total,
                paidTotal = paidTotal
            )
        )

        val allWarnings = pageResults.flatMap { it.result.warnings }.toMutableList()
        if (pageResults.size > 1 && (subtotal != null || total != null)) {
            allWarnings.add("Multi-page merge used last-page totals")
        }
        allWarnings.addAll(reconResult.warnings)

        val avgConfidence = if (pageResults.isNotEmpty()) {
            pageResults.map { it.result.confidence }.average()
        } else 0.0
        val confidence = (avgConfidence * 0.5 + reconResult.confidence * 0.5).coerceIn(0.0, 1.0)

        return ParsedReceiptResult(
            merchantName = merchant,
            purchaseTime = purchaseTime,
            currency = currency,
            items = dedupedItems,
            subtotal = reconResult.subtotal,
            tax = reconResult.tax,
            total = reconResult.total,
            paidTotal = reconResult.paidTotal ?: reconResult.total,
            warnings = allWarnings.distinct(),
            confidence = confidence,
            parserVendor = "mlkit",
            parserVersion = "v1.1"
        )
    }

    private fun dedupeItems(items: List<ParsedReceiptItem>): List<ParsedReceiptItem> {
        val seen = mutableSetOf<Pair<String, Double>>()
        return items.filter { item ->
            val key = item.rawText.trim().lowercase() to item.lineTotal
            if (seen.contains(key)) false else {
                seen.add(key)
                true
            }
        }
    }
}
