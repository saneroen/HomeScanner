package org.nighthawklabs.homescanner.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nighthawklabs.homescanner.domain.model.Receipt
import org.nighthawklabs.homescanner.domain.model.ReceiptStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScanClick: () -> Unit,
    onReceiptClick: (String) -> Unit
) {
    val receipts by viewModel.receipts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Receipt Scanner") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Receipt")
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(receipts, key = { it.id }) { receipt ->
                    ReceiptRow(
                        receipt = receipt,
                        onClick = { onReceiptClick(receipt.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    receipt: Receipt,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val dateStr = receipt.purchaseTime?.let { dateFormat.format(Date(it)) }
        ?: dateFormat.format(Date(receipt.createdAt))
    val imagePath = receipt.processedImagePath ?: receipt.rawImagePath
    val file = if (imagePath.isNotEmpty()) File(imagePath) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (file != null && file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(end = 12.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.displayTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall
                )
                if (receipt.pageCount > 1) {
                    Text(
                        text = "Pages: ${receipt.pageCount}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor(receipt.status)
                    ) {
                        Text(
                            text = receipt.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (receipt.isEdited) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    receipt.total?.let { total ->
                        Text(
                            text = "$${"%.2f".format(total)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun statusColor(status: ReceiptStatus) = when (status) {
    ReceiptStatus.CAPTURING -> Color.Gray.copy(alpha = 0.3f)
    ReceiptStatus.CONFIRMED -> Color(0xFF4CAF50).copy(alpha = 0.3f)
    ReceiptStatus.NEEDS_REVIEW -> Color(0xFFFF9800).copy(alpha = 0.3f)
    ReceiptStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.3f)
    ReceiptStatus.PROCESSING_IMAGE, ReceiptStatus.PARSING -> Color(0xFF2196F3).copy(alpha = 0.3f)
    else -> Color.Gray.copy(alpha = 0.3f)
}
