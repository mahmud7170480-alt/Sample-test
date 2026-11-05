package com.example.ratecalc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun InvoiceScreen(items: List<Product>, onShare: () -> Unit, onClose: () -> Unit) {
    val now = androidx.compose.runtime.remember { System.currentTimeMillis() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onShare, enabled = items.isNotEmpty()) {
                        Icon(Icons.Default.Share, "Share invoice", tint = Color.White)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 28.dp)) {
                        Text(
                            "INVOICE", color = Color.Black, fontSize = 44.sp, fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            formatDay(now), color = Color.DarkGray, fontSize = 15.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        InvoiceRow("Item", "Rate", "Qty", "Total", Color.DarkGray, FontWeight.Normal)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.LightGray)
                        if (items.isEmpty()) {
                            Text("No items selected.", color = Color.Black, modifier = Modifier.padding(vertical = 16.dp))
                        }
                        items.forEach { p ->
                            InvoiceRow(p.name, money(p.rate), Calc.qtyLabel(p), money(Calc.lineTotal(p)), Color.Black, FontWeight.Normal)
                        }
                        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp), color = Color.LightGray)
                        Text(
                            "Grand Total: " + money(items.sumOf { Calc.lineTotal(it) }),
                            color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun InvoiceRow(a: String, b: String, c: String, d: String, color: Color, weight: FontWeight) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Text(a, Modifier.weight(1f), color = color, fontWeight = weight, fontSize = 15.sp)
        Text(b, Modifier.width(64.dp), color = color, fontWeight = weight, fontSize = 15.sp, textAlign = TextAlign.End)
        Text(c, Modifier.width(56.dp), color = color, fontWeight = weight, fontSize = 15.sp, textAlign = TextAlign.End)
        Text(d, Modifier.width(78.dp), color = color, fontWeight = weight, fontSize = 15.sp, textAlign = TextAlign.End)
    }
}
