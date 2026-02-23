package org.nighthawklabs.homescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item_category_learning")
data class ItemCategoryLearningEntity(
    @PrimaryKey val key: String,
    val category: String,
    val confidence: Double,
    val updatedAt: Long,
    val source: String
)
