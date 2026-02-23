package org.nighthawklabs.homescanner.domain.parser

import java.io.File

/**
 * Abstraction for receipt parsing. Implementations may use an on-device SDK or a stub fallback.
 */
interface ReceiptParser {
    suspend fun parse(imageFile: File): ParsedReceiptResult
}
