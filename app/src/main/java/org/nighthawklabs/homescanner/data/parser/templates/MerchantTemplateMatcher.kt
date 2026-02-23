package org.nighthawklabs.homescanner.data.parser.templates

object MerchantTemplateMatcher {

    fun match(merchantName: String, templates: List<MerchantTemplate>): MerchantTemplate? {
        val normalized = normalizeForMatch(merchantName)
        if (normalized.isEmpty()) return null

        templates
            .firstOrNull { it.merchantKey.equals(normalized, ignoreCase = true) }
            ?.let { return it }

        templates
            .firstOrNull { normalized.contains(it.merchantKey, ignoreCase = true) }
            ?.let { return it }

        val merchantTokens = normalized.split(Regex("\\s+")).toSet()
        return templates
            .maxByOrNull { template ->
                val keyTokens = template.merchantKey.split(Regex("\\s+")).toSet()
                (merchantTokens intersect keyTokens).size
            }
            ?.takeIf { template ->
                val keyTokens = template.merchantKey.split(Regex("\\s+")).toSet()
                (merchantTokens intersect keyTokens).size >= keyTokens.size / 2
            }
    }

    private fun normalizeForMatch(s: String): String =
        s.lowercase()
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
