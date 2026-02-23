package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem

data class ReconciliationResult(
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val paidTotal: Double?,
    val warnings: List<String>,
    val confidence: Double
)

/**
 * Constrained reconciliation: prefers item-derived values when they satisfy constraints.
 * Only trusts parsed summary values if they match item sums within tolerance.
 */
object ReceiptTotalsReconciler {

    private const val TOLERANCE = 0.02 // 2 cents
    private const val RELAXED_TOLERANCE = 0.50 // fallback for noisy receipts

    fun reconcile(
        items: List<ParsedReceiptItem>,
        candidates: SummaryCandidates
    ): ReconciliationResult {
        val warnings = mutableListOf<String>()
        var confidenceModifier = 0.0
        val tolerance = if (items.isEmpty()) RELAXED_TOLERANCE else TOLERANCE

        val computedSubtotal = items.sumOf { it.lineTotal }
        val tax = candidates.tax ?: 0.0
        val discount = candidates.discount ?: 0.0
        val fee = candidates.fee ?: 0.0
        val computedTotal = computedSubtotal + tax + fee - discount

        val subtotal = when {
            candidates.subtotal != null -> {
                val diff = kotlin.math.abs(computedSubtotal - candidates.subtotal!!)
                if (diff <= tolerance) candidates.subtotal
                else if (items.isNotEmpty()) {
                    warnings.add("Subtotal mismatch: items sum $${"%.2f".format(computedSubtotal)} vs parsed $${"%.2f".format(candidates.subtotal)}")
                    confidenceModifier -= 0.15
                    candidates.subtotal
                } else candidates.subtotal
            }
            else -> {
                if (items.isNotEmpty()) {
                    warnings.add("Subtotal inferred from items")
                }
                computedSubtotal
            }
        }

        val total = when {
            candidates.total != null -> {
                val diff = kotlin.math.abs(computedTotal - candidates.total!!)
                if (diff <= tolerance) candidates.total
                else if (items.isNotEmpty()) {
                    warnings.add("Total mismatch: computed $${"%.2f".format(computedTotal)} vs parsed $${"%.2f".format(candidates.total)}")
                    confidenceModifier -= 0.15
                    candidates.total
                } else candidates.total
            }
            else -> computedTotal
        }

        if (candidates.tax == null && tax == 0.0) {
            warnings.add("Tax not found")
        }

        var conf = 0.90
        if (candidates.total == null) conf -= 0.25
        if (items.isEmpty()) conf -= 0.5
        conf += confidenceModifier

        return ReconciliationResult(
            subtotal = subtotal,
            tax = tax,
            total = total,
            paidTotal = candidates.paidTotal,
            warnings = warnings,
            confidence = conf.coerceIn(0.0, 1.0)
        )
    }

    @Deprecated("Use reconcile(items, SummaryCandidates)")
    fun reconcile(
        items: List<ParsedReceiptItem>,
        subtotal: Double?,
        tax: Double?,
        total: Double?
    ): Pair<List<String>, Double> {
        val candidates = SummaryCandidates(subtotal = subtotal, tax = tax, total = total)
        val result = reconcile(items, candidates)
        return result.warnings to (result.confidence - 0.85).coerceIn(-1.0, 0.0)
    }
}
