package org.nighthawklabs.homescanner.data.parser.layout

object OcrNormalizer {

    fun normalizeText(s: String): String = s
        .normalizeWhitespace()
        .normalizePunctuation()
        .normalizeDecimalSeparators()

    private fun String.normalizeWhitespace(): String =
        replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.normalizePunctuation(): String =
        replace(Regex("""[|¦]"""), "|")
            .replace(Regex("""['']"""), "'")
            .replace(Regex("""[""]"""), "\"")
            .replace(Regex("""[–—]"""), "-")
            .replace(Regex("""…"""), "...")

    private fun String.normalizeDecimalSeparators(): String =
        replace(Regex("""(\d),(\d{3}(?:,\d{3})*\.?\d*)""")) { mr ->
            mr.groupValues[1] + mr.groupValues[2].replace(",", "")
        }
            .replace(Regex("""(\d),(\d{2})(?:\s|$)""")) { mr ->
                mr.groupValues[1] + "." + mr.groupValues[2]
            }
}
