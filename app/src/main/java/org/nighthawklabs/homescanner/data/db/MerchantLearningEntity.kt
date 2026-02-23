package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_learning")
data class MerchantLearningEntity(
    @PrimaryKey val merchantKey: String,
    val ignoreRegexesJson: String,
    val aliasMapJson: String,
    val preferredTwoAmountColumns: Boolean? = null,
    val keywordOverridesJson: String? = null,
    val updatedAt: Long
)
