package org.nighthawklabs.homescanner.domain.model

data class ReceiptItemEditable(
    val lineId: String,
    var rawText: String,
    var itemName: String,
    var qty: Double,
    var unitPrice: Double?,
    var lineTotal: Double,
    var paidPrice: Double,
    var categoryHint: String? = null,
    var kind: ItemKind = ItemKind.ITEM,
    var deleted: Boolean = false
)

data class ReceiptSummaryEditable(
    var subtotal: Double,
    var tax: Double,
    var total: Double,
    var paidTotal: Double
)

data class ReceiptWorkingSet(
    val items: MutableList<ReceiptItemEditable>,
    val summary: ReceiptSummaryEditable,
    val warnings: List<String>,
    val confidence: Double,
    val originalParsedJson: String,
    val merchantName: String,
    val currency: String
) {
    fun itemsForComputation(): List<ReceiptItemEditable> =
        items.filter { !it.deleted && it.kind == ItemKind.ITEM }

    fun computedSubtotal(): Double = itemsForComputation().sumOf { it.lineTotal }
    fun computedTax(): Double = items.filter { !it.deleted && it.kind == ItemKind.TAX }.sumOf { it.lineTotal }
    fun computedDiscount(): Double = items.filter { !it.deleted && it.kind == ItemKind.DISCOUNT }.sumOf { it.lineTotal }
    fun computedFee(): Double = items.filter { !it.deleted && it.kind == ItemKind.FEE }.sumOf { it.lineTotal }
    fun computedTotal(): Double = computedSubtotal() + computedTax() + computedFee() - computedDiscount()
}
