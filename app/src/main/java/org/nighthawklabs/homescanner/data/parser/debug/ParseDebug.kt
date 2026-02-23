package org.nighthawklabs.homescanner.data.parser.debug

import android.util.Log

private const val TAG = "ReceiptParse"

object ParseDebug {

    fun logStage(
        stage: String,
        receiptId: String,
        pageIndex: Int?,
        itemCount: Int,
        extra: Map<String, Any?> = emptyMap()
    ) {
        try {
            val extras = extra.entries.joinToString(", ") { (k, v) -> "$k=$v" }
            Log.d(TAG, "[$stage] receiptId=$receiptId pageIndex=$pageIndex itemCount=$itemCount $extras")
        } catch (_: RuntimeException) {
            // Log not available in unit tests
        }
    }

    fun logPerPageParse(receiptId: String, pageIndex: Int, itemCount: Int, topLines: List<String>, skippedReason: String?) {
        logStage("PER_PAGE", receiptId, pageIndex, itemCount, mapOf(
            "topLines" to topLines.take(5).toString(),
            "skippedReason" to skippedReason
        ))
    }

    fun logPostMerge(receiptId: String, mergedItemCount: Int, perPageCounts: List<Int>, topItems: List<String>) {
        logStage("POST_MERGE", receiptId, null, mergedItemCount, mapOf(
            "perPageCounts" to perPageCounts.toString(),
            "topItems" to topItems.take(5).toString()
        ))
    }

    fun logSchemaBeforeSerialize(receiptId: String, itemCount: Int, hasItemsField: Boolean, topItemNames: List<String>) {
        logStage("SCHEMA_PRE", receiptId, null, itemCount, mapOf(
            "hasItemsField" to hasItemsField,
            "topItemNames" to topItemNames.take(5).toString()
        ))
    }

    fun logSchemaAfterSerialize(receiptId: String, jsonLength: Int, containsItemsKey: Boolean, itemCountInJson: Int?) {
        logStage("SCHEMA_POST", receiptId, null, itemCountInJson ?: 0, mapOf(
            "jsonLength" to jsonLength,
            "containsItemsKey" to containsItemsKey,
            "itemCountInJson" to itemCountInJson
        ))
    }
}
