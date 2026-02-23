package org.nighthawklabs.homescanner.ui.receipt.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nighthawklabs.homescanner.domain.model.ItemKind
import org.nighthawklabs.homescanner.domain.model.ReceiptItemEditable
import org.nighthawklabs.homescanner.domain.model.ReceiptWorkingSet

@Composable
fun ItemsFixModeContent(
    workingSet: ReceiptWorkingSet,
    onItemChanged: (ReceiptItemEditable) -> Unit,
    onMarkKind: (ReceiptItemEditable, ItemKind) -> Unit,
    onDelete: (ReceiptItemEditable) -> Unit,
    onAddItem: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onConfirm: () -> Unit
) {
    val subtotal = workingSet.computedSubtotal()
    val tax = workingSet.computedTax()
    val fee = workingSet.computedFee()
    val discount = workingSet.computedDiscount()
    val computedTotal = subtotal + tax + fee - discount
    val diff = workingSet.summary.total - computedTotal
    val hasMismatch = kotlin.math.abs(diff) > 0.01

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Fix mode", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Summary (computed)", style = MaterialTheme.typography.titleSmall)
                Text("Subtotal: $%.2f".format(subtotal))
                Text("Tax: $%.2f".format(tax))
                if (fee > 0) Text("Fees: $%.2f".format(fee))
                if (discount > 0) Text("Discount: -$%.2f".format(discount))
                Text("Total: $%.2f".format(computedTotal))
                if (hasMismatch) {
                    Text("Diff vs parsed: $%.2f".format(diff), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(workingSet.items.filter { !it.deleted }, key = { it.lineId }) { item ->
                EditableItemRow(
                    item = item,
                    onItemChanged = onItemChanged,
                    onMarkKind = onMarkKind,
                    onDelete = onDelete
                )
            }
            item {
                Button(
                    onClick = onAddItem,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add item")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onSave) { Text("Save fixes") }
            Button(onClick = onConfirm) { Text("Confirm Receipt") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableItemRow(
    item: ReceiptItemEditable,
    onItemChanged: (ReceiptItemEditable) -> Unit,
    onMarkKind: (ReceiptItemEditable, ItemKind) -> Unit,
    onDelete: (ReceiptItemEditable) -> Unit
) {
    var name by remember(item.lineId) { mutableStateOf(item.itemName) }
    var qtyStr by remember(item.lineId) { mutableStateOf(item.qty.toString()) }
    var unitPriceStr by remember(item.lineId) { mutableStateOf(item.unitPrice?.toString() ?: "") }
    var lineTotalStr by remember(item.lineId) { mutableStateOf(item.lineTotal.toString()) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (item.kind) {
                ItemKind.ITEM -> MaterialTheme.colorScheme.surface
                ItemKind.FEE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ItemKind.DISCOUNT -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ItemKind.TAX -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; item.itemName = it; onItemChanged(item) },
                    label = { Text("Item") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(text = { Text("Mark as Fee") }, onClick = {
                            onMarkKind(item, ItemKind.FEE); showMenu = false
                        })
                        DropdownMenuItem(text = { Text("Mark as Discount") }, onClick = {
                            onMarkKind(item, ItemKind.DISCOUNT); showMenu = false
                        })
                        DropdownMenuItem(text = { Text("Mark as Tax") }, onClick = {
                            onMarkKind(item, ItemKind.TAX); showMenu = false
                        })
                        DropdownMenuItem(text = { Text("Mark as Not item") }, onClick = {
                            onMarkKind(item, ItemKind.OTHER); showMenu = false
                        })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            onDelete(item); showMenu = false
                        })
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { qtyStr = it; item.qty = it.toDoubleOrNull() ?: 1.0; onItemChanged(item) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(0.5f)
                )
                OutlinedTextField(
                    value = unitPriceStr,
                    onValueChange = { unitPriceStr = it; item.unitPrice = it.toDoubleOrNull(); onItemChanged(item) },
                    label = { Text("Unit $") },
                    modifier = Modifier.weight(0.5f)
                )
                OutlinedTextField(
                    value = lineTotalStr,
                    onValueChange = { lineTotalStr = it; item.lineTotal = it.toDoubleOrNull() ?: 0.0; onItemChanged(item) },
                    label = { Text("Total") },
                    modifier = Modifier.weight(0.5f)
                )
            }
            if (item.kind != ItemKind.ITEM) {
                Text("(${item.kind})", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
