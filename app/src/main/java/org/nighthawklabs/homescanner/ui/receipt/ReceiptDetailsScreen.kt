package org.nighthawklabs.homescanner.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nighthawklabs.homescanner.data.parser.ReceiptParsedSchema
import org.nighthawklabs.homescanner.domain.model.ItemKind
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.domain.model.ReceiptItemEditable
import org.nighthawklabs.homescanner.BuildConfig
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet
import org.nighthawklabs.homescanner.ui.receipt.debug.ReceiptParserDebugScreen
import org.nighthawklabs.homescanner.ui.receipt.items.ItemsFixModeContent
import org.nighthawklabs.homescanner.work.WorkProgress
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailsScreen(
    viewModel: ReceiptDetailsViewModel,
    onBack: () -> Unit,
    onEditPage: (String, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val receipt = uiState.receipt
    var selectedTab by remember { mutableStateOf(0) }
    var expandedEditMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.ingestionResult) {
        uiState.ingestionResult?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearIngestionResult()
        }
    }
    LaunchedEffect(selectedTab, uiState.selectedPageIndex, receipt?.id, receipt?.parseJson) {
        if (BuildConfig.DEBUG && selectedTab == 3 && receipt != null) {
            viewModel.loadPageDebug(receipt.id, uiState.selectedPageIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(receipt?.displayTitle ?: "Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (receipt != null && (receipt.pages.isNotEmpty() || receipt.rawImagePath.isNotEmpty())) {
                        Box {
                            IconButton(onClick = {
                                if (receipt.pages.size <= 1) {
                                    onEditPage(receipt.id, 0)
                                } else {
                                    expandedEditMenu = true
                                }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit scan")
                            }
                            if (receipt.pages.size > 1) {
                                androidx.compose.material3.DropdownMenu(
                                    expanded = expandedEditMenu,
                                    onDismissRequest = { expandedEditMenu = false }
                                ) {
                                    receipt.pages.forEachIndexed { idx, _ ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Edit page ${idx + 1}") },
                                            onClick = {
                                                onEditPage(receipt.id, idx)
                                                expandedEditMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (receipt == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                StatusBanner(
                    receipt = receipt,
                    workProgress = uiState.workProgress,
                    onConfirm = { viewModel.confirmReceipt() },
                    onRetry = { viewModel.retryParse() },
                    onCancel = { viewModel.cancelProcessing() }
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scan") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Items") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("JSON") }
                    )
                    if (BuildConfig.DEBUG) {
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("Debug") }
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> ScanTab(
                            receipt = receipt,
                            selectedPageIndex = uiState.selectedPageIndex,
                            onPageSelected = { viewModel.setSelectedPage(it) },
                            onEditPage = { pageIdx -> onEditPage(receipt.id, pageIdx) }
                        )
                        1 -> ItemsTab(
                            receipt = receipt,
                            parsedSchema = uiState.parsedSchema,
                            isFixMode = uiState.isFixMode,
                            workingSet = uiState.workingSet,
                            onFixMode = { viewModel.enterFixMode() },
                            onCancelFix = { viewModel.exitFixMode() },
                            onItemChanged = { viewModel.updateItem(it) },
                            onMarkKind = { item, kind -> viewModel.markItemKind(item, kind) },
                            onDeleteItem = { viewModel.deleteItem(it) },
                            onAddItem = { viewModel.addItem() },
                            onSaveFixes = { viewModel.saveFixes() },
                            onConfirm = { viewModel.confirmReceipt() }
                        )
                        2 -> JsonTab(
                            receipt = receipt,
                            jsonViewMode = uiState.jsonViewMode,
                            onViewModeChange = { viewModel.setJsonViewMode(it) },
                            onCopy = {
                                viewModel.copyJsonToClipboard()
                                scope.launch { snackbarHostState.showSnackbar("Copied") }
                            }
                        )
                        3 -> if (BuildConfig.DEBUG) {
                            val displayPages = if (receipt.pages.isNotEmpty()) receipt.pages
                                else listOf(org.nighthawklabs.homescanner.domain.model.ReceiptPage(
                                    receiptId = receipt.id, pageIndex = 0,
                                    rawImagePath = receipt.rawImagePath,
                                    processedImagePath = receipt.processedImagePath,
                                    status = "CAPTURED_RAW", createdAt = 0L
                                ))
                            val pageIdx = uiState.selectedPageIndex.coerceIn(0, maxOf(0, displayPages.size - 1))
                            val page = displayPages.getOrNull(pageIdx)
                            ReceiptParserDebugScreen(
                                receiptId = receipt.id,
                                pageIndex = pageIdx,
                                page = page,
                                pageDebug = uiState.pageDebug,
                                onReparse = { viewModel.triggerReparse() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize())
                        }
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    receipt: Receipt,
    workProgress: WorkProgress?,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    when (receipt.status) {
        ReceiptStatus.PROCESSING_IMAGE, ReceiptStatus.PARSING -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(workProgress?.message ?: "Processing…", style = MaterialTheme.typography.bodyMedium)
                            if (workProgress != null) {
                                Text("${workProgress.percent}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text("Cancel")
                    }
                }
            }
        }
        ReceiptStatus.NEEDS_REVIEW -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Needs review", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onConfirm) { Text("Confirm Receipt") }
                }
            }
        }
        ReceiptStatus.CONFIRMED -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "Confirmed",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        ReceiptStatus.FAILED -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(receipt.errorMessage ?: "Failed", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun ScanTab(
    receipt: Receipt,
    selectedPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onEditPage: (Int) -> Unit
) {
    val pages = receipt.pages
    val displayPages = if (pages.isNotEmpty()) pages else listOf(
        org.nighthawklabs.homescanner.domain.model.ReceiptPage(
            receiptId = receipt.id,
            pageIndex = 0,
            rawImagePath = receipt.rawImagePath,
            processedImagePath = receipt.processedImagePath,
            status = "CAPTURED_RAW",
            createdAt = 0L
        )
    ).filter { it.rawImagePath.isNotEmpty() }
    val selectedPage = displayPages.getOrNull(selectedPageIndex.coerceIn(0, displayPages.size - 1))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (receipt.status == ReceiptStatus.PROCESSING_IMAGE || receipt.status == ReceiptStatus.PARSING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Processing scan…")
            }
        }
        if (displayPages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayPages.size) { idx ->
                    val page = displayPages[idx]
                    val file = File(page.displayImagePath)
                    val isSelected = idx == selectedPageIndex
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            .clickable { onPageSelected(idx) }
                    ) {
                        if (file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Page ${idx + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (selectedPage != null) {
                val file = File(selectedPage.displayImagePath)
                Text(
                    text = "Page ${selectedPageIndex + 1} of ${displayPages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Receipt page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = { onEditPage(selectedPageIndex) },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Edit page ${selectedPageIndex + 1}")
                }
            }
        }
    }
}

@Composable
private fun ItemsTab(
    receipt: Receipt,
    parsedSchema: ReceiptParsedSchema?,
    isFixMode: Boolean,
    workingSet: ReceiptWorkingSet?,
    onFixMode: () -> Unit,
    onCancelFix: () -> Unit,
    onItemChanged: (ReceiptItemEditable) -> Unit,
    onMarkKind: (ReceiptItemEditable, ItemKind) -> Unit,
    onDeleteItem: (ReceiptItemEditable) -> Unit,
    onAddItem: () -> Unit,
    onSaveFixes: () -> Unit,
    onConfirm: () -> Unit
) {
    if (isFixMode && workingSet != null) {
        ItemsFixModeContent(
            workingSet = workingSet,
            onItemChanged = onItemChanged,
            onMarkKind = onMarkKind,
            onDelete = onDeleteItem,
            onAddItem = onAddItem,
            onCancel = onCancelFix,
            onSave = onSaveFixes,
            onConfirm = onConfirm
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (parsedSchema == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Extracting items…")
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    receipt.parseConfidence?.let { conf ->
                        Text("Parser confidence: %.0f%%".format(conf * 100), style = MaterialTheme.typography.labelMedium)
                    }
                    if (parsedSchema.warnings.isNotEmpty()) {
                        Text("${parsedSchema.warnings.size} warning(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (parsedSchema.items.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No items detected.", style = MaterialTheme.typography.titleSmall)
                            Text("No line items extracted — OCR may be missing prices. Try Edit Scan or Fix mode.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (parsedSchema.warnings.any { it.contains("Fallback") }) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Fallback item extraction was used. Review items for accuracy.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            parsedSchema.summary?.let { summary ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary", style = MaterialTheme.typography.titleSmall)
                            Text("Subtotal: $%.2f".format(summary.subtotal))
                            Text("Tax: $%.2f".format(summary.tax))
                            Text("Total: $%.2f".format(summary.total))
                        }
                    }
                }
            }
            items(parsedSchema.items, key = { it.lineId }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.itemName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%.0f x $%.2f".format(item.qty, item.unitPrice),
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (item.confidence < 0.9) {
                                Text("Low confidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("$%.2f".format(item.lineTotal), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (parsedSchema.warnings.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Warnings", style = MaterialTheme.typography.titleSmall)
                            parsedSchema.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (receipt.status == ReceiptStatus.NEEDS_REVIEW) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onFixMode) { Text("Fix") }
                        Button(onClick = onConfirm) { Text("Confirm Receipt") }
                    }
                }
            } else if (receipt.status == ReceiptStatus.CONFIRMED) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        Text("Confirmed")
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonTab(
    receipt: Receipt,
    jsonViewMode: JsonViewMode,
    onViewModeChange: (JsonViewMode) -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            receipt.parseConfidence?.let { Text("Confidence: %.0f%%".format(it * 100)) }
            if (receipt.correctedJson != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onViewModeChange(JsonViewMode.EFFECTIVE) },
                        enabled = jsonViewMode != JsonViewMode.EFFECTIVE
                    ) { Text("Corrected") }
                    TextButton(
                        onClick = { onViewModeChange(JsonViewMode.PARSED) },
                        enabled = jsonViewMode != JsonViewMode.PARSED
                    ) { Text("Parsed") }
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
        }
        val json = when (jsonViewMode) {
            JsonViewMode.CORRECTED -> receipt.correctedJson
            JsonViewMode.PARSED -> receipt.parseJson
            JsonViewMode.EFFECTIVE -> receipt.effectiveJson
        } ?: "{}"
        Text(
            text = json,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
