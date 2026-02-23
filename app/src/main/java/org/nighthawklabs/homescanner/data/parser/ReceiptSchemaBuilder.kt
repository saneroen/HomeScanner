package org.nighthawklabs.homescanner.data.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.data.parser.debug.ParseDebug
import org.nighthawklabs.homescanner.domain.model.ItemKind
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptItem
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult

object ReceiptSchemaBuilder {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun buildReceiptJson(receiptId: String, parsed: ParsedReceiptResult): String {
        val items = parsed.items
        val itemSchemas = items.map { item ->
            ReceiptItemSchema(
                lineId = item.lineId,
                rawText = item.rawText,
                itemId = null,
                itemName = item.itemName,
                qty = item.qty,
                unitPrice = item.unitPrice,
                lineTotal = item.lineTotal,
                paidPrice = item.paidPrice,
                confidence = item.confidence
            )
        }
        val summary = ReceiptSummarySchema(
            subtotal = parsed.subtotal ?: 0.0,
            tax = parsed.tax ?: 0.0,
            total = parsed.total ?: 0.0,
            paidTotal = parsed.paidTotal ?: parsed.total ?: 0.0
        )
        val schema = ReceiptParsedSchema(
            receiptId = receiptId,
            currency = parsed.currency,
            merchant = ReceiptMerchant(name = parsed.merchantName ?: "Receipt"),
            purchaseTime = parsed.purchaseTime?.let { formatIso8601(it) },
            items = itemSchemas,
            summary = summary,
            warnings = if (items.isEmpty()) parsed.warnings + "No line items extracted" else parsed.warnings,
            parser = ReceiptParserInfo(
                vendor = parsed.parserVendor,
                version = parsed.parserVersion,
                confidence = parsed.confidence
            ),
            debugTrace = parsed.debugTrace?.copy(receiptId = receiptId)
        )
        ParseDebug.logSchemaBeforeSerialize(receiptId, itemSchemas.size, true, itemSchemas.map { it.itemName })
        val encoded = json.encodeToString(schema)
        val containsItems = encoded.contains("\"items\"")
        ParseDebug.logSchemaAfterSerialize(receiptId, encoded.length, containsItems, items.size)
        return encoded
    }

    fun buildFromWorkingSet(receiptId: String, workingSet: ReceiptWorkingSet): String {
        val subtotal = workingSet.computedSubtotal()
        val tax = workingSet.computedTax()
        val fee = workingSet.computedFee()
        val discount = workingSet.computedDiscount()
        val total = subtotal + tax + fee - discount
        val items = workingSet.items
            .filter { !it.deleted && it.kind == ItemKind.ITEM }
            .map { item ->
                ReceiptItemSchema(
                    lineId = item.lineId,
                    rawText = item.rawText,
                    itemId = null,
                    itemName = item.itemName,
                    qty = item.qty,
                    unitPrice = item.unitPrice ?: item.lineTotal / item.qty,
                    lineTotal = item.lineTotal,
                    paidPrice = item.paidPrice,
                    confidence = 1.0
                )
            }
        val summary = ReceiptSummarySchema(
            subtotal = subtotal,
            tax = tax,
            total = total,
            paidTotal = total
        )
        val schema = ReceiptParsedSchema(
            receiptId = receiptId,
            currency = workingSet.currency,
            merchant = ReceiptMerchant(name = workingSet.merchantName),
            purchaseTime = null,
            items = items,
            summary = summary,
            warnings = workingSet.warnings,
            parser = ReceiptParserInfo(vendor = "mlkit", version = "v1.1", confidence = workingSet.confidence)
        )
        return json.encodeToString(schema)
    }

    private fun formatIso8601(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMs
        return "%04d-%02d-%02dT%02d:%02d:%02d.000Z".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }
}
