package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.domain.model.ItemKind
import org.nighthawklabs.homescanner.domain.model.ReceiptItemEditable
import org.nighthawklabs.homescanner.domain.model.ReceiptSummaryEditable
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet

object ReceiptWorkingSetConverter {

    fun fromParsedSchema(schema: ReceiptParsedSchema, originalJson: String = ""): ReceiptWorkingSet {
        val items = schema.items.map { item ->
            ReceiptItemEditable(
                lineId = item.lineId,
                rawText = item.rawText,
                itemName = item.itemName,
                qty = item.qty,
                unitPrice = item.unitPrice,
                lineTotal = item.lineTotal,
                paidPrice = item.paidPrice,
                kind = ItemKind.ITEM,
                deleted = false
            )
        }.toMutableList()
        val summary = schema.summary?.let {
            ReceiptSummaryEditable(
                subtotal = it.subtotal,
                tax = it.tax,
                total = it.total,
                paidTotal = it.paidTotal
            )
        } ?: ReceiptSummaryEditable(0.0, 0.0, 0.0, 0.0)
        return ReceiptWorkingSet(
            items = items,
            summary = summary,
            warnings = schema.warnings,
            confidence = schema.parser?.confidence ?: 0.0,
            originalParsedJson = originalJson,
            merchantName = schema.merchant.name,
            currency = schema.currency
        )
    }
}
