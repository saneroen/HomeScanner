package org.nighthawklabs.homescanner.data.parser

import android.graphics.BitmapFactory
import java.io.File

object ImageDimensions {

    fun get(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return (opts.outWidth ?: 1000) to (opts.outHeight ?: 1000)
    }
}
