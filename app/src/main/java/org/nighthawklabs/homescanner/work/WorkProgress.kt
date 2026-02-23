package org.nighthawklabs.homescanner.work

import androidx.work.Data

data class WorkProgress(
    val stage: String,
    val percent: Int,
    val message: String
) {
    companion object {
        fun from(data: Data): WorkProgress? {
            val stage = data.getString(ReceiptWorkConstants.KEY_STAGE) ?: return null
            val percent = data.getInt(ReceiptWorkConstants.KEY_PERCENT, 0)
            val message = data.getString(ReceiptWorkConstants.KEY_MESSAGE) ?: ""
            return WorkProgress(stage = stage, percent = percent, message = message)
        }
    }

    fun toData(): Data = Data.Builder()
        .putString(ReceiptWorkConstants.KEY_STAGE, stage)
        .putInt(ReceiptWorkConstants.KEY_PERCENT, percent)
        .putString(ReceiptWorkConstants.KEY_MESSAGE, message)
        .build()
}
