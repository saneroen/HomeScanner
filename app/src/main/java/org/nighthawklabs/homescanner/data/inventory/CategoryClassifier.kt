package org.nighthawklabs.homescanner.data.inventory

import org.nighthawklabs.homescanner.data.db.InventoryDao
import org.nighthawklabs.homescanner.domain.inventory.InventoryCategory

data class ClassificationResult(
    val category: InventoryCategory,
    val confidence: Double,
    val source: String
)

class CategoryClassifier(
    private val inventoryDao: InventoryDao
) {
    private val servicesKeywords = setOf(
        "uber", "lyft", "delivery", "service", "labor", "installation",
        "tip", "gratuity", "subscription", "membership", "fee"
    )
    private val foodKeywords = setOf(
        "milk", "eggs", "bread", "chicken", "rice", "banana", "apple", "carrot",
        "yogurt", "beef", "pasta", "cheese", "butter", "salad", "soup", "coffee",
        "tea", "juice", "water", "cereal", "flour", "sugar", "oil", "tomato",
        "onion", "potato", "fruit", "vegetable", "meat", "fish", "cracker",
        "cookie", "chocolate", "ice cream", "soda", "beer", "wine"
    )
    private val foodUnits = setOf("lb", "oz", "kg", "g", "ml", "l", "ct", "ea")

    private val homeKeywords = setOf(
        "chair", "table", "lamp", "sofa", "towel", "detergent", "soap", "shampoo",
        "battery", "bulb", "light", "furniture", "kitchen", "storage", "organizer",
        "cleaning", "trash", "bag", "paper", "tissue", "sponge", "brush"
    )

    suspend fun classify(
        itemName: String,
        rawText: String,
        merchantName: String? = null
    ): ClassificationResult {
        val normalizedKey = Normalization.normalizeKey(itemName)
        if (normalizedKey.isBlank()) return ClassificationResult(InventoryCategory.OTHER, 0.5, "DEFAULT")

        val learning = inventoryDao.getLearningForKey(normalizedKey)
        if (learning != null && learning.confidence >= 0.7) {
            return ClassificationResult(
                InventoryCategory.valueOf(learning.category),
                learning.confidence,
                learning.source
            )
        }

        val lowerName = normalizedKey.lowercase()
        val lowerRaw = rawText.lowercase()

        if (servicesKeywords.any { lowerName.contains(it) || lowerRaw.contains(it) }) {
            return ClassificationResult(InventoryCategory.SERVICES, 0.85, "RULE")
        }
        if (foodKeywords.any { lowerName.contains(it) || lowerRaw.contains(it) }) {
            return ClassificationResult(InventoryCategory.FOOD, 0.9, "RULE")
        }
        if (foodUnits.any { lowerRaw.contains(it) }) {
            return ClassificationResult(InventoryCategory.FOOD, 0.75, "RULE")
        }
        if (homeKeywords.any { lowerName.contains(it) || lowerRaw.contains(it) }) {
            return ClassificationResult(InventoryCategory.HOME, 0.85, "RULE")
        }

        return ClassificationResult(InventoryCategory.OTHER, 0.5, "RULE")
    }
}
