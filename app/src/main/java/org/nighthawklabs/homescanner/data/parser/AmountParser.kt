package org.nighthawklabs.homescanner.data.parser

data class AmountMatch(
    val value: Double,
    val spanStart: Int,
    val spanEnd: Int
)

object AmountParser {

    private val amountRegex = Regex(
        """(?<!\w)[\$\€\£]?\s*\(?-?\d{1,3}(?:,\d{3})*(?:\.\d{2})\)?(?!\w)|
        (?<!\w)\d+\.\d{2}(?!\w)""".trimMargin().replace("\n", "")
    )

    fun findAmounts(text: String): List<AmountMatch> =
        amountRegex.findAll(text).mapNotNull { match ->
            val raw = match.value
            parseAmountToken(raw)?.let { AmountMatch(it, match.range.first, match.range.last + 1) }
        }.toList()

    fun parseAmountToken(token: String): Double? {
        val cleaned = token
            .replace(Regex("""[\$\€\£\s]"""), "")
            .replace(",", "")
            .replace("(", "-")
            .replace(")", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    fun extractAmounts(line: String): List<Double> = findAmounts(line).map { it.value }
}

object DateExtractor {

    private val datePatterns = listOf(
        Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})"""),
        Regex("""(\d{1,2})-(\d{1,2})-(\d{2,4})"""),
        Regex("""(\d{4})-(\d{2})-(\d{2})""")
    )

    fun extractDate(line: String): Long? {
        for (pattern in datePatterns) {
            val match = pattern.find(line) ?: continue
            val groups = match.groupValues
            if (groups.size < 4) continue
            return try {
                when (pattern) {
                    datePatterns[0], datePatterns[1] -> {
                        val m = groups[1].toInt() - 1
                        val d = groups[2].toInt()
                        val y = groups[3].toInt().let { if (it < 100) 2000 + it else it }
                        toEpochMillis(m, d, y)
                    }
                    datePatterns[2] -> {
                        val y = groups[1].toInt()
                        val m = groups[2].toInt() - 1
                        val d = groups[3].toInt()
                        toEpochMillis(m, d, y)
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun toEpochMillis(month: Int, day: Int, year: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, month)
        cal.set(java.util.Calendar.DAY_OF_MONTH, day)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
