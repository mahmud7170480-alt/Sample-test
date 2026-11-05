package com.example.ratecalc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProductDialog(
    initial: Product?,
    onSave: (String, Double, Double, Double, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var rate by remember { mutableStateOf(initial?.let { num(it.rate) } ?: "") }
    var cc by remember { mutableStateOf(initial?.let { num(it.cc) } ?: "") }
    var pot by remember { mutableStateOf(initial?.let { num(it.pot) } ?: "") }
    var unit by remember { mutableStateOf(initial?.soldUnit ?: "Normal") }
    var error by remember { mutableStateOf<String?>(null) }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add product" else "Edit product") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text("Rate (৳)") }, singleLine = true,
                    keyboardOptions = decimal, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cc, onValueChange = { cc = it },
                        label = { Text("Tablets per CC") }, singleLine = true,
                        keyboardOptions = decimal, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pot, onValueChange = { pot = it },
                        label = { Text("Tablets per Pot") }, singleLine = true,
                        keyboardOptions = decimal, modifier = Modifier.weight(1f)
                    )
                }
                Text("Sold as", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Calc.units.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = rate.toDoubleOrNull()
                if (name.isBlank()) {
                    error = "Enter a name"
                } else if (r == null || r < 0) {
                    error = "Enter a valid rate"
                } else {
                    onSave(name.trim(), r, cc.toDoubleOrNull() ?: 0.0, pot.toDoubleOrNull() ?: 0.0, unit)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun QtyDialog(product: Product, onSet: (Int) -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(product.quantity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Quantity" + if (product.soldUnit != "Normal") " (${product.soldUnit})" else "") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSet(text.toIntOrNull() ?: 0) }) { Text("Set") } },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Edit product") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun RenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProfilesDialog(
    profiles: List<Profile>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profiles") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                profiles.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.id == currentId, onClick = { onSelect(p.id); onDismiss() })
                        Text(p.name, Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { renaming = p; renameText = p.name }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = { deleting = p }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                Text(
                    "A new profile is named with the current date & time and keeps your product list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(""); onDismiss() }) {
                Text("New profile")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    renaming?.let { p ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { onRename(p.id, renameText.trim()); renaming = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = "Delete profile?",
            text = "\"${p.name}\" and all of its products will be deleted.",
            onConfirm = { onDelete(p.id); deleting = null },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
fun SaveHistoryDialog(onNew: () -> Unit, onOverwrite: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save History") },
        text = { Text("How would you like to save this history?\n\n“Overwrite Existing” replaces the latest saved invoice.") },
        confirmButton = { TextButton(onClick = onNew) { Text("Save as New") } },
        dismissButton = { TextButton(onClick = onOverwrite) { Text("Overwrite Existing") } }
    )
}

@Composable
fun FontSizeDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("Small" to 0.85f, "Medium" to 1.0f, "Large" to 1.15f, "Extra large" to 1.3f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font size") },
        text = {
            Column {
                options.forEach { (label, scale) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(scale) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = kotlin.math.abs(current - scale) < 0.01f, onClick = { onSelect(scale) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun RatesDialog(onExport: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export / Import rates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Rates are saved as a CSV file: Name, Rate, CC, Pot, SoldUnit.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Export rates") }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import rates") }
                Text(
                    "Import adds new products and updates the rates of products with the same name. Quantities are not changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** "Today" or pick any date with Material's date picker. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DateChoiceDialog(title: String, onToday: () -> Unit, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (!picking) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text("Set the date to today (${formatDay(System.currentTimeMillis())}) or choose a date yourself.") },
            confirmButton = { TextButton(onClick = onToday) { Text("Today") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { picking = true }) { Text("Choose date") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    } else {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtc(System.currentTimeMillis()))
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = { state.selectedDateMillis?.let { onPicked(utcDayToLocal(it)) } }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}
