package org.nighthawklabs.homescanner.domain.model

data class MerchantLearning(
    val merchantKey: String,
    val ignoreRegexes: List<String>,
    val aliasMap: Map<String, String>,
    val preferredTwoAmountColumns: Boolean? = null,
    val keywordOverridesJson: String? = null,
    val updatedAt: Long
)
