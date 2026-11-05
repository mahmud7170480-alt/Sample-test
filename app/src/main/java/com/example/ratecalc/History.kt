package com.example.ratecalc

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun decode(f: File, sample: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(f.path, o)?.asImageBitmap()
}

@Composable
fun HistoryTab(
    files: List<File>,
    onShare: (File) -> Unit,
    onGallery: (File) -> Unit,
    onDelete: (File) -> Unit,
    onUpdateDate: (File, Long) -> Unit
) {
    var viewingPath by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var toUpdate by remember { mutableStateOf<File?>(null) }

    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint("No saved invoices yet.\nTap History on the Calculate tab to save one as a PNG.")
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(files, key = { it.path }) { f ->
                InvoiceCard(
                    f,
                    onOpen = { viewingPath = f.path },
                    onShare = { onShare(f) },
                    onGallery = { onGallery(f) },
                    onDelete = { toDelete = f },
                    onUpdateDate = { toUpdate = f }
                )
            }
        }
    }

    viewingPath?.let { path ->
        val f = files.firstOrNull { it.path == path }
        if (f == null) {
            viewingPath = null
        } else {
            InvoiceViewer(
                f,
                onShare = { onShare(f) },
                onUpdateDate = { toUpdate = f },
                onClose = { viewingPath = null }
            )
        }
    }
    toUpdate?.let { f ->
        DateChoiceDialog(
            title = "Update date",
            onToday = { onUpdateDate(f, System.currentTimeMillis()); toUpdate = null },
            onPicked = { t -> onUpdateDate(f, t); toUpdate = null },
            onDismiss = { toUpdate = null }
        )
    }
    toDelete?.let { f ->
        ConfirmDialog(
            title = "Delete invoice?",
            text = "${f.name} will be removed from history.",
            onConfirm = { onDelete(f); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun InvoiceCard(
    f: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onGallery: () -> Unit,
    onDelete: () -> Unit,
    onUpdateDate: () -> Unit
) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, f.path, f.lastModified()) { value = decode(f, 2) }
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(230.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpen)
            ) {
                bmp?.let {
                    Image(
                        bitmap = it, contentDescription = f.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.TopCenter
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f).padding(start = 4.dp), fontSize = 15.sp, maxLines = 2)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Save to gallery") }, onClick = { menu = false; onGallery() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            FilledTonalButton(onClick = onUpdateDate, modifier = Modifier.fillMaxWidth()) { Text("Update date") }
        }
    }
}

@Composable
private fun InvoiceViewer(f: File, onShare: () -> Unit, onUpdateDate: () -> Unit, onClose: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, f.path, f.lastModified()) { value = decode(f, 1) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    bmp?.let {
                        Image(
                            bitmap = it, contentDescription = f.name,
                            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
                        )
                    }
                }
                Button(
                    onClick = onUpdateDate,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                ) { Text("Update date") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
