package org.nighthawklabs.homescanner.data.parser

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nighthawklabs.homescanner.data.parser.layout.ColumnInferer
import org.nighthawklabs.homescanner.data.parser.layout.OcrRow
import org.nighthawklabs.homescanner.data.parser.layout.OcrLayoutBuilder
import org.nighthawklabs.homescanner.data.parser.layout.RowClusterer
import org.nighthawklabs.homescanner.data.parser.layout.RowJoiner
import org.nighthawklabs.homescanner.data.parser.templates.MerchantTemplateApplier
import org.nighthawklabs.homescanner.data.parser.templates.MerchantTemplateStore
import org.nighthawklabs.homescanner.data.parser.debug.DebugCandidate
import org.nighthawklabs.homescanner.data.parser.debug.DebugLine
import org.nighthawklabs.homescanner.data.parser.debug.DebugRow
import org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug
import org.nighthawklabs.homescanner.domain.parser.ParsedReceiptResult
import org.nighthawklabs.homescanner.domain.parser.ReceiptParser
import java.io.File

/**
 * Production on-device receipt parser using ML Kit Text Recognition v2.
 * Pipeline: layout-first row reconstruction, row joiner, merchant filter (ignoreRegex + alias),
 * candidate extraction, constrained reconciliation, relaxed fallback when 0 items.
 * Stores debug trace for tuning.
 */
class MlKitReceiptParser(
    private val context: Context
) : ReceiptParser {

    private val textExtractor = MlKitTextExtractor()

    suspend fun parsePage(receiptId: String, pageIndex: Int, imageFile: File): ParsedReceiptResult =
        withContext(Dispatchers.Default) {
            doParse(imageFile, receiptId, pageIndex)
        }

    override suspend fun parse(imageFile: File): ParsedReceiptResult = withContext(Dispatchers.Default) {
        try {
            doParse(imageFile, null, null)
        } catch (e: ReceiptParserException) {
            throw e
        } catch (e: Exception) {
            throw ReceiptParserException("ML Kit parse failed: ${e.message}", e)
        }
    }

    private suspend fun doParse(
        imageFile: File,
        receiptId: String?,
        pageIndex: Int?
    ): ParsedReceiptResult = try {
        val mlKitResult = textExtractor.extractText(context, imageFile)
        val layout = OcrLayoutBuilder.build(mlKitResult.text, mlKitResult.imageWidth, mlKitResult.imageHeight)
            if (layout.allLines.isEmpty()) {
                throw ReceiptParserException("No text extracted from image")
            }

            val rowsCluster = RowClusterer.clusterRows(layout.allLines, layout.imageHeight)
            if (rowsCluster.isEmpty()) {
                throw ReceiptParserException("No rows detected")
            }

            var columnModel = ColumnInferer.inferColumns(rowsCluster, layout.imageWidth)
            val joinResult = RowJoiner.joinWrappedPricesWithStats(rowsCluster, columnModel)
            val rowsJoined = joinResult.rows
            val merchantName = guessMerchant(rowsJoined)
            val template = MerchantTemplateStore.findTemplate(merchantName ?: "")
            val filteredRows = MerchantTemplateApplier.filterRows(rowsJoined, template)
            columnModel = MerchantTemplateApplier.applyColumnOverride(columnModel, template)

            val ignoreRegexes = template?.compiledIgnoreRegexes() ?: emptyList()
            val itemAliasMap = template?.itemAliasMappings ?: emptyMap()

            val extractResult = ReceiptItemExtractor.extractItems(
                rows = filteredRows,
                columnModel = columnModel,
                ignoreRegexes = ignoreRegexes,
                itemAliasMap = itemAliasMap,
                buildTrace = true
            )
            val items = extractResult.items
            val summaryCandidates = ReceiptSummaryExtractor.extract(filteredRows, columnModel)
            val reconResult = ReceiptTotalsReconciler.reconcile(items, summaryCandidates)

            val purchaseTime = filteredRows.firstNotNullOfOrNull { DateExtractor.extractDate(it.text) }

            val currency = detectCurrency(filteredRows)
            val paidTotal = summaryCandidates.paidTotal ?: reconResult.total
            val allWarnings = reconResult.warnings.toMutableList()
            if (extractResult.usedFallback) {
                allWarnings.add("Fallback item extraction used")
            }
            if (extractResult.usedRelaxed) {
                allWarnings.add("Relaxed fallback used (minimal filtering)")
            }

            val usedPath = when {
                extractResult.usedRelaxed -> "relaxed"
                extractResult.usedFallback -> "fallback"
                else -> "strict"
            }
            val debugTrace = ParseDebugTrace(
                rowsAfterCluster = rowsCluster.map { it.text },
                rowsAfterJoin = rowsJoined.map { it.text },
                rowsAfterFilter = filteredRows.map { it.text },
                rowRejections = extractResult.rejections,
                strictItemCount = extractResult.strictCount,
                fallbackItemCount = extractResult.fallbackCount,
                relaxedItemCount = extractResult.relaxedCount,
                usedPath = usedPath
            )
            Log.d("ReceiptParse", debugTrace.toLogString())

            val pageDebug = if (receiptId != null && pageIndex != null) {
                buildPageParseDebug(
                    receiptId = receiptId,
                    pageIndex = pageIndex,
                    layout = layout,
                    rowsCluster = rowsCluster,
                    rowsJoined = rowsJoined,
                    filteredRows = filteredRows,
                    extractResult = extractResult,
                    joinResult = joinResult,
                    summaryRowsCount = filteredRows.count { ReceiptSummaryExtractor.summaryKeywords(it.text) },
                    itemRowsCount = items.size
                )
            } else null

            ParsedReceiptResult(
                merchantName = merchantName ?: "Receipt",
                purchaseTime = purchaseTime,
                currency = currency,
                items = items,
                subtotal = reconResult.subtotal,
                tax = reconResult.tax,
                total = reconResult.total,
                paidTotal = paidTotal,
                warnings = allWarnings,
                confidence = reconResult.confidence,
                parserVendor = "mlkit",
                parserVersion = "v2.0",
                debugTrace = debugTrace,
                pageParseDebug = pageDebug
            )
    } catch (e: ReceiptParserException) {
        throw e
    } catch (e: Exception) {
        throw ReceiptParserException("ML Kit parse failed: ${e.message}", e)
    }

    private fun buildPageParseDebug(
        receiptId: String,
        pageIndex: Int,
        layout: org.nighthawklabs.homescanner.data.parser.layout.OcrLayout,
        rowsCluster: List<OcrRow>,
        rowsJoined: List<OcrRow>,
        filteredRows: List<OcrRow>,
        extractResult: ReceiptItemExtractor.ExtractResult,
        joinResult: org.nighthawklabs.homescanner.data.parser.layout.RowJoinerResult,
        summaryRowsCount: Int,
        itemRowsCount: Int
    ): PageParseDebug {
        val lines = layout.allLines
        val debugLines = lines.map { line ->
            val b = line.box
            DebugLine(
                text = line.text,
                left = if (b.width() > 0) b.left else null,
                top = if (b.height() > 0) b.top else null,
                right = if (b.width() > 0) b.right else null,
                bottom = if (b.height() > 0) b.bottom else null
            )
        }
        val unplaced = RowClusterer.getUnplacedLineTexts(lines)
        val debugRows = filteredRows.mapIndexed { idx, row ->
            val amounts = AmountParser.findAmounts(row.text).map { it.value }
            val isSum = ReceiptSummaryExtractor.summaryKeywords(row.text)
            val rej = extractResult.rejections.find { it.rowIndex == idx }
            DebugRow(
                text = row.text.take(200),
                left = row.box.left,
                top = row.box.top,
                right = row.box.right,
                bottom = row.box.bottom,
                amountsFound = amounts,
                isSummary = isSum,
                rejectedReasonStrict = rej?.reason,
                rejectedReasonRelaxed = null,
                joined = false
            )
        }
        val rejectionSummary = extractResult.rejections.groupingBy { it.reason }.eachCount()
        val strictCandidates = extractResult.items.take(extractResult.strictCount).map {
            DebugCandidate(it.itemName, it.rawText, it.lineTotal, it.confidence, "strict")
        }
        val fallbackCandidates = extractResult.items.drop(extractResult.strictCount).take(extractResult.fallbackCount).map {
            DebugCandidate(it.itemName, it.rawText, it.lineTotal, it.confidence, "fallback")
        }
        val relaxedCandidates = extractResult.items.drop(extractResult.strictCount + extractResult.fallbackCount).map {
            DebugCandidate(it.itemName, it.rawText, it.lineTotal, it.confidence, "relaxed")
        }
        return PageParseDebug(
            receiptId = receiptId,
            pageIndex = pageIndex,
            imageWidth = layout.imageWidth,
            imageHeight = layout.imageHeight,
            lines = debugLines,
            rows = debugRows,
            itemCandidatesStrict = strictCandidates,
            itemCandidatesRelaxed = relaxedCandidates.ifEmpty { fallbackCandidates },
            rejectionSummary = rejectionSummary,
            sampleOcrText = lines.joinToString("\n") { it.text }.take(500),
            rowsCountAfterCluster = rowsCluster.size,
            rowsCountAfterJoin = rowsJoined.size,
            joinsPerformed = joinResult.joinsPerformed,
            summaryRowsCount = summaryRowsCount,
            itemRowsCount = itemRowsCount,
            unplacedLines = unplaced
        )
    }

    private fun guessMerchant(rows: List<OcrRow>): String? {
        val topRows = rows.take(5)
        for (row in topRows) {
            val text = row.text.trim()
            if (text.length in 3..60 &&
                !AmountParser.findAmounts(text).any { it.value > 0 } &&
                !ReceiptSummaryExtractor.summaryKeywords(text)
            ) {
                return text.takeIf { it.any { c -> c.isLetter() } }
            }
        }
        return null
    }

    private fun detectCurrency(rows: List<OcrRow>): String {
        val allText = rows.joinToString(" ") { it.text }
        return when {
            allText.contains("€") -> "EUR"
            allText.contains("£") -> "GBP"
            allText.contains("₹") -> "INR"
            else -> "USD"
        }
    }
}
