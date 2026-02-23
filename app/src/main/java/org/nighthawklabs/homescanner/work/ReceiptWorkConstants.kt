package org.nighthawklabs.homescanner.work

object ReceiptWorkConstants {

    const val CHANNEL_ID = "receipt_processing"
    const val CHANNEL_NAME = "Receipt Processing"
    const val NOTIFICATION_ID = 1001

    const val KEY_RECEIPT_ID = "receipt_id"
    const val KEY_PROCESSED_PATH = "processed_path"
    const val KEY_PAGE_COUNT = "page_count"

    const val KEY_STAGE = "stage"
    const val KEY_PERCENT = "percent"
    const val KEY_MESSAGE = "message"

    const val STAGE_PROCESSING_IMAGE = "PROCESSING_IMAGE"
    const val STAGE_PARSING = "PARSING"

    const val TAG_RECEIPT = "receipt"

    fun workName(receiptId: String): String = "process_receipt_$receiptId"
    fun tagReceipt(receiptId: String): String = "receipt:$receiptId"
}
