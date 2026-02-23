package org.nighthawklabs.homescanner.data.parser.templates

import org.nighthawklabs.homescanner.data.parser.layout.ColumnModel
import org.nighthawklabs.homescanner.data.parser.layout.OcrRow

object MerchantTemplateApplier {

    fun filterRows(rows: List<OcrRow>, template: MerchantTemplate?): List<OcrRow> {
        if (template == null) return rows
        val regexes = template.compiledIgnoreRegexes()
        if (regexes.isEmpty()) return rows
        return rows.filter { row ->
            regexes.none { it.containsMatchIn(row.text) }
        }
    }

    fun applyColumnOverride(columnModel: ColumnModel, template: MerchantTemplate?): ColumnModel {
        if (template?.hasTwoAmountColumns == null) return columnModel
        return ColumnModel(
            descriptionRegionXMax = columnModel.descriptionRegionXMax,
            amountColumnXCenters = columnModel.amountColumnXCenters,
            hasTwoAmountColumns = template.hasTwoAmountColumns
        )
    }
}
