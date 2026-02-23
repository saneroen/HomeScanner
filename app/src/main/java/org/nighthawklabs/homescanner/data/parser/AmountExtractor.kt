package org.nighthawklabs.homescanner.data.parser

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object AmountExtractor {

    private val priceRegex = Regex("""\d+\.\d{2}""")

    private val datePatterns = listOf(
        Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})"""),
        Regex("""(\d{1,2})-(\d{1,2})-(\d{2,4})"""),
        Regex("""(\d{4})-(\d{2})-(\d{2})""")
    )

    fun extractAmounts(line: String): List<Double> =
        priceRegex.findAll(line)
            .map { it.value.toDouble() }
            .toList()

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
                        parseToEpochMillis(m, d, y)
                    }
                    datePatterns[2] -> {
                        val y = groups[1].toInt()
                        val m = groups[2].toInt() - 1
                        val d = groups[3].toInt()
                        parseToEpochMillis(m, d, y)
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun parseToEpochMillis(month: Int, day: Int, year: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
