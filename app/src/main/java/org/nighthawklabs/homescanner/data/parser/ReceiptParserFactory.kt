package org.nighthawklabs.homescanner.data.parser

import org.nighthawklabs.homescanner.domain.parser.ReceiptParser

object ReceiptParserFactory {

    /** Set to false to use stub parser only (e.g. for testing). */
    const val USE_REAL_SDK: Boolean = true

    fun create(): ReceiptParser = when {
        USE_REAL_SDK -> SdkReceiptParser(ReceiptSdkEngine())
        else -> FallbackStubReceiptParser()
    }
}
