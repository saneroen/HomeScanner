package org.nighthawklabs.homescanner.ui.receipt.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nighthawklabs.homescanner.BuildConfig
import org.nighthawklabs.homescanner.data.parser.debug.PageParseDebug
import org.nighthawklabs.homescanner.domain.model.ReceiptPage
import java.io.File

@Composable
fun ReceiptParserDebugScreen(
    receiptId: String,
    pageIndex: Int,
    page: ReceiptPage?,
    pageDebug: PageParseDebug?,
    onReparse: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return
    var selectedRowIndex by remember { mutableStateOf<Int?>(null) }
    var imageDisplayWidth by remember { mutableStateOf(0f) }
    var imageDisplayHeight by remember { mutableStateOf(0f) }
    var imageOffsetX by remember { mutableStateOf(0f) }
    var imageOffsetY by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (pageDebug != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Stats", style = MaterialTheme.typography.titleSmall)
                    Text("Image: ${pageDebug.imageWidth} x ${pageDebug.imageHeight}")
                    Text("Blocks/Lines: ${pageDebug.lines.size} lines")
                    val nullBbox = pageDebug.lines.count { it.left == null }
                    Text("Lines with null bbox: $nullBbox (${if (pageDebug.lines.isEmpty()) 0 else nullBbox * 100 / pageDebug.lines.size}%)")
                    val tops = pageDebug.lines.mapNotNull { it.top }
                    val bottoms = pageDebug.lines.mapNotNull { it.bottom }
                    val lefts = pageDebug.lines.mapNotNull { it.left }
                    val rights = pageDebug.lines.mapNotNull { it.right }
                    if (tops.isNotEmpty()) {
                        Text("minTop=${tops.minOrNull()}, maxBottom=${bottoms.maxOrNull()}")
                        Text("minLeft=${lefts.minOrNull()}, maxRight=${rights.maxOrNull()}")
                    }
                    Text("Rows after cluster: ${pageDebug.rowsCountAfterCluster}, after join: ${pageDebug.rowsCountAfterJoin}, joins: ${pageDebug.joinsPerformed}")
                    Text("Summary rows: ${pageDebug.summaryRowsCount}, Item rows: ${pageDebug.itemRowsCount}")
                }
            }
            Button(
                onClick = onReparse,
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Re-run parse (debug)")
            }
        }

        if (page != null) {
            val imagePath = page.processedImagePath ?: page.rawImagePath
            if (imagePath.isNotBlank()) {
                val file = File(imagePath)
                if (file.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 500.dp)
                        .padding(8.dp)
                        .onSizeChanged { size ->
                            val imgW = pageDebug?.imageWidth?.toFloat() ?: 1f
                            val imgH = pageDebug?.imageHeight?.toFloat() ?: 1f
                            val scale = minOf(size.width / imgW, size.height / imgH)
                            imageDisplayWidth = imgW * scale
                            imageDisplayHeight = imgH * scale
                            imageOffsetX = (size.width - imageDisplayWidth) / 2
                            imageOffsetY = (size.height - imageDisplayHeight) / 2
                        }
                ) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Receipt page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )
                    if (pageDebug != null && imageDisplayWidth > 0) {
                        DebugOverlayCanvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            imagePixelWidth = pageDebug.imageWidth,
                            imagePixelHeight = pageDebug.imageHeight,
                            displayImageWidth = imageDisplayWidth,
                            displayImageHeight = imageDisplayHeight,
                            imageOffsetX = imageOffsetX,
                            imageOffsetY = imageOffsetY,
                            lines = pageDebug.lines,
                            rows = pageDebug.rows,
                            selectedRowIndex = selectedRowIndex,
                            drawRowBoxes = true
                        )
                    }
                }
                }
            }
        }

        if (pageDebug != null && pageDebug.rows.isNotEmpty()) {
            Text(
                "Rows (tap to highlight)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                pageDebug.rows.forEachIndexed { idx, row ->
                    val isSelected = selectedRowIndex == idx
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRowIndex = if (isSelected) null else idx },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                row.text.take(80) + if (row.text.length > 80) "…" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "amt=${row.amountsFound} sum=${row.isSummary} rej=${row.rejectedReasonStrict}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
