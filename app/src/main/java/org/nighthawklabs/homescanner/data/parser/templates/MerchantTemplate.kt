package org.nighthawklabs.homescanner.data.parser.templates

import kotlinx.serialization.Serializable

@Serializable
data class MerchantTemplate(
    val merchantKey: String,
    val ignoreRegexes: List<String> = emptyList(),
    val subtotalKeywords: List<String> = emptyList(),
    val totalKeywords: List<String> = emptyList(),
    val taxKeywords: List<String> = emptyList(),
    val hasTwoAmountColumns: Boolean? = null,
    val currencyOverride: String? = null,
    val dateRegexes: List<String> = emptyList(),
    /** Maps raw item text (lowercase, trimmed) to normalized item name */
    val itemAliasMappings: Map<String, String> = emptyMap()
) {
    fun compiledIgnoreRegexes(): List<Regex> = ignoreRegexes.map { Regex(it, RegexOption.IGNORE_CASE) }
}
