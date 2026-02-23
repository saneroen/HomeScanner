package org.nighthawklabs.homescanner.data.files

import android.content.Context
import java.io.File

class FileStore(private val context: Context) {

    private val receiptsDir: File
        get() {
            val dir = File(context.filesDir, "receipts")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun newRawReceiptPath(draftId: String): String =
        newRawReceiptPath(draftId, 0)

    fun newRawReceiptPath(draftId: String, pageIndex: Int): String {
        val pageDir = File(File(receiptsDir, draftId), "pages").let { base ->
            File(base, pageIndex.toString()).also { if (!it.exists()) it.mkdirs() }
        }
        return File(pageDir, "raw.jpg").absolutePath
    }

    fun newCroppedReceiptPath(draftId: String): String =
        newProcessedReceiptPath(draftId, 0)

    fun newProcessedReceiptPath(draftId: String, pageIndex: Int): String {
        val pageDir = File(File(receiptsDir, draftId), "pages").let { base ->
            File(base, pageIndex.toString()).also { if (!it.exists()) it.mkdirs() }
        }
        return File(pageDir, "processed.jpg").absolutePath
    }
}
