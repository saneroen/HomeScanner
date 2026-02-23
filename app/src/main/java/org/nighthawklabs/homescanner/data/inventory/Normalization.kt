package org.nighthawklabs.homescanner.data.inventory

object Normalization {
    private val packSizeRegex = Regex("""\b\d+\s*(ct|oz|lb|kg|ml|g|pk|pkg|ea|ea\.)\b""", RegexOption.IGNORE_CASE)

    /**
     * Normalize item name to a canonical key for deduplication.
     * - lowercase, trim, collapse spaces
     * - remove punctuation
     * - optionally strip pack size tokens (12ct, 16oz) cautiously
     */
    fun normalizeKey(name: String, stripPackSize: Boolean = true): String {
        if (name.isBlank()) return ""
        var s = name
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\p{Punct}]+"), "")
        if (stripPackSize) {
            s = packSizeRegex.replace(s, "").trim().replace(Regex("\\s+"), " ")
        }
        return s.trim().ifBlank { "" }
    }
}
