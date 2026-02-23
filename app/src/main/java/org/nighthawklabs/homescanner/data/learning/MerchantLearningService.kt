package org.nighthawklabs.homescanner.data.learning

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nighthawklabs.homescanner.data.db.MerchantLearningDao
import org.nighthawklabs.homescanner.data.db.MerchantLearningEntity
import org.nighthawklabs.homescanner.domain.model.MerchantLearning
import org.nighthawklabs.homescanner.domain.model.ReceiptItemEditable

object MerchantKeyNormalizer {

    fun normalize(merchantName: String): String =
        merchantName
            .lowercase()
            .replace(Regex("""[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

object MerchantLearningService {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLearning(dao: MerchantLearningDao, merchantKey: String): MerchantLearning? {
        val entity = dao.getLearning(merchantKey) ?: return null
        return toDomain(entity)
    }

    suspend fun mergeSignals(
        dao: MerchantLearningDao,
        merchantKey: String,
        aliasSignals: Map<String, String>,
        ignorePatterns: List<String>
    ) {
        val existing = dao.getLearning(merchantKey)
        val now = System.currentTimeMillis()

        val mergedAliasMap = mutableMapOf<String, String>()
        existing?.let {
            val existingMap = runCatching {
                json.decodeFromString<Map<String, String>>(it.aliasMapJson)
            }.getOrElse { emptyMap() }
            mergedAliasMap.putAll(existingMap)
        }
        mergedAliasMap.putAll(aliasSignals)

        val mergedIgnore = mutableSetOf<String>()
        existing?.let {
            val existingList = runCatching {
                json.decodeFromString<List<String>>(it.ignoreRegexesJson)
            }.getOrElse { emptyList() }
            mergedIgnore.addAll(existingList)
        }
        mergedIgnore.addAll(ignorePatterns)

        val entity = MerchantLearningEntity(
            merchantKey = merchantKey,
            ignoreRegexesJson = json.encodeToString(mergedIgnore.toList()),
            aliasMapJson = json.encodeToString(mergedAliasMap),
            preferredTwoAmountColumns = existing?.preferredTwoAmountColumns,
            keywordOverridesJson = existing?.keywordOverridesJson,
            updatedAt = now
        )
        dao.upsert(entity)
    }

    fun extractAliasSignals(
        originalItems: List<ReceiptItemEditable>,
        editedItems: List<ReceiptItemEditable>
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (edited in editedItems) {
            if (edited.deleted) continue
            val original = originalItems.find { it.lineId == edited.lineId } ?: continue
            if (normalizeForAlias(original.itemName) != normalizeForAlias(edited.itemName)) {
                val key = normalizeForAlias(original.rawText)
                if (key.isNotEmpty()) {
                    result[key] = normalizeForAlias(edited.itemName)
                }
            }
        }
        return result
    }

    fun extractIgnorePatterns(itemsMarkedNotItem: List<ReceiptItemEditable>): List<String> =
        itemsMarkedNotItem.mapNotNull { item ->
            val escaped = Regex.escape(item.rawText.trim())
            if (escaped.length in 3..80) "^$escaped$" else null
        }

    private fun normalizeForAlias(s: String): String =
        s.lowercase().trim().replace(Regex("""\s+"""), " ")

    private fun toDomain(entity: MerchantLearningEntity): MerchantLearning {
        val ignoreRegexes = runCatching {
            json.decodeFromString<List<String>>(entity.ignoreRegexesJson)
        }.getOrElse { emptyList() }
        val aliasMap = runCatching {
            json.decodeFromString<Map<String, String>>(entity.aliasMapJson)
        }.getOrElse { emptyMap() }
        return MerchantLearning(
            merchantKey = entity.merchantKey,
            ignoreRegexes = ignoreRegexes,
            aliasMap = aliasMap,
            preferredTwoAmountColumns = entity.preferredTwoAmountColumns,
            keywordOverridesJson = entity.keywordOverridesJson,
            updatedAt = entity.updatedAt
        )
    }
}
