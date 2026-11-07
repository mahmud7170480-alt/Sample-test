@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ratecalc

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

@Composable
private fun cardColors() = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
fun AppRoot(vm: MainViewModel, fontScale: Float, onFontScale: (Float) -> Unit) {
    val ctx = LocalContext.current
    val profiles by vm.profiles.collectAsState()
    val profile by vm.currentProfile.collectAsState()
    val products by vm.products.collectAsState()
    val invoices by vm.invoices.collectAsState()
    val focus = LocalFocusManager.current
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val pendingRestore by vm.pendingRestore.collectAsState()
    var confirmUR by remember { mutableStateOf<String?>(null) }
    var showRates by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showTitleDate by remember { mutableStateOf(false) }
    val undoUi = UndoUi(canUndo, canRedo, { confirmUR = "undo" }, { confirmUR = "redo" })

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showProfiles by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showInvoice by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var qtyFor by remember { mutableStateOf<Product?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var clearCcFor by remember { mutableStateOf<Product?>(null) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.createBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }

    val selected = remember(products) { products.filter { it.quantity > 0 } }
    val grandTotal = remember(products) { products.sumOf { Calc.lineTotal(it) } }
    val filtered = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products else products.filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showProfiles = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, "Profiles", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    profile?.name ?: "…",
                    modifier = Modifier.weight(1f),
                    fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2
                )
                if (tab == 1) {
                    IconButton(onClick = { focus.clearFocus() }) { Icon(Icons.Default.Check, "Done") }
                } else {
                    IconButton(onClick = { showTitleDate = true }) { Icon(Icons.Default.Edit, "Change date") }
                }
                IconButton(onClick = { showInvoice = true }) { Icon(Icons.Default.Share, "Invoice") }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Profiles") }, onClick = { showMenu = false; showProfiles = true })
                        DropdownMenuItem(text = { Text("Rename profile") }, onClick = { showMenu = false; renameTitle = true })
                        DropdownMenuItem(text = { Text("Font size") }, onClick = { showMenu = false; showFont = true })
                        DropdownMenuItem(text = { Text("Export / Import rates") }, onClick = { showMenu = false; showRates = true })
                        DropdownMenuItem(text = { Text("Backup & restore") }, onClick = { showMenu = false; showBackup = true })
                    }
                }
            }
        },
        bottomBar = {
            Column {
                TotalBar(
                    total = grandTotal,
                    showHistoryButton = tab == 0,
                    clearLabel = if (tab == 0) "Clear All" else "Clear",
                    canSave = selected.isNotEmpty(),
                    onHistory = { showSave = true },
                    onClear = { confirmClear = true }
                )
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.ShoppingCart, null) }, label = { Text("Calculate") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Star, null) }, label = { Text("CC") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Menu, null) }, label = { Text("History") })
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) { Icon(Icons.Default.Add, "Add product") }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when (tab) {
                0 -> CalculateTab(filtered, products.isEmpty(), query, { query = it }, onTap = { qtyFor = it }, undo = undoUi)
                1 -> StockTab(
                    products = products,
                    query = query,
                    onQuery = { query = it },
                    onEdit = { editing = it },
                    onValue = { p, v -> vm.setQty(p, Calc.qtyFromStock(p, v)) },
                    onClearRequest = { clearCcFor = it },
                    undo = undoUi
                )
                else -> HistoryTab(
                    files = invoices,
                    onShare = { InvoiceImage.shareFile(ctx, it) },
                    onGallery = { vm.saveToGallery(it) },
                    onDelete = { vm.deleteInvoice(it) },
                    onUpdateDate = { f, t -> vm.updateInvoiceDate(f, t) }
                )
            }
        }
    }

    if (showProfiles) {
        ProfilesDialog(
            profiles = profiles,
            currentId = profile?.id,
            onSelect = { vm.switchProfile(it) },
            onCreate = { vm.createProfile(it) },
            onRename = { id, n -> vm.renameProfile(id, n) },
            onDelete = { vm.deleteProfile(it) },
            onDismiss = { showProfiles = false }
        )
    }
    if (renameTitle) {
        RenameDialog(
            initial = profile?.name ?: "",
            onSave = { n -> profile?.let { vm.renameProfile(it.id, n) }; renameTitle = false },
            onDismiss = { renameTitle = false }
        )
    }
    if (showAdd) {
        ProductDialog(
            initial = null,
            onSave = { n, r, c, p, u -> vm.addProduct(n, r, c, p, u); showAdd = false },
            onDelete = null,
            onDismiss = { showAdd = false }
        )
    }
    editing?.let { p ->
        ProductDialog(
            initial = p,
            onSave = { n, r, c, pt, u -> vm.updateProduct(p, n, r, c, pt, u); editing = null },
            onDelete = { vm.deleteProduct(p); editing = null },
            onDismiss = { editing = null }
        )
    }
    qtyFor?.let { p ->
        QtyDialog(
            product = p,
            onSet = { vm.setQty(p, it); qtyFor = null },
            onEdit = { qtyFor = null; editing = p },
            onDismiss = { qtyFor = null }
        )
    }
    if (showInvoice) {
        InvoiceScreen(
            items = selected,
            onShare = { InvoiceImage.share(ctx, selected) },
            onClose = { showInvoice = false }
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all quantities?",
            text = "All quantities in this profile will be reset to 0.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearQuantities(); confirmClear = false },
            onDismiss = { confirmClear = false }
        )
    }
    if (showSave) {
        SaveHistoryDialog(
            onNew = { vm.saveInvoice(selected, false); showSave = false },
            onOverwrite = { vm.saveInvoice(selected, true); showSave = false },
            onDismiss = { showSave = false }
        )
    }
    if (showFont) {
        FontSizeDialog(current = fontScale, onSelect = onFontScale, onDismiss = { showFont = false })
    }
    confirmUR?.let { which ->
        val undoing = which == "undo"
        ConfirmDialog(
            title = if (undoing) "Undo last change?" else "Redo change?",
            text = if (undoing) "The last quantity change will be reverted." else "The change you undid will be applied again.",
            confirmLabel = if (undoing) "Undo" else "Redo",
            onConfirm = { if (undoing) vm.undo() else vm.redo(); confirmUR = null },
            onDismiss = { confirmUR = null }
        )
    }
    if (showTitleDate) {
        DateChoiceDialog(
            title = "Change date",
            onToday = { vm.setTitleDate(System.currentTimeMillis()); showTitleDate = false },
            onPicked = { t -> vm.setTitleDate(t); showTitleDate = false },
            onDismiss = { showTitleDate = false }
        )
    }
    if (showBackup) {
        BackupDialog(
            onBackup = { showBackup = false; backupLauncher.launch(Backup.fileName(System.currentTimeMillis())) },
            onRestore = { showBackup = false; restoreLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showBackup = false }
        )
    }
    pendingRestore?.let { b ->
        ConfirmDialog(
            title = "Restore backup?",
            text = (if (b.createdAt > 0) "Backup from ${formatDay(b.createdAt)}\n" else "") +
                "${b.profiles.size} profiles, ${b.productCount} products, ${b.invoiceCount} saved invoices.\n\n" +
                "This will REPLACE all current data on this phone.",
            confirmLabel = "Restore",
            onConfirm = { vm.confirmRestore() },
            onDismiss = { vm.cancelRestore() }
        )
    }
    if (showRates) {
        RatesDialog(
            onExport = { showRates = false; exportLauncher.launch("rates.csv") },
            onImport = { showRates = false; importLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showRates = false }
        )
    }
    clearCcFor?.let { p ->
        val what = if (Calc.stockIsPot(p)) "Pot" else "CC"
        ConfirmDialog(
            title = "Clear $what?",
            text = "Reset $what for ${p.name} to 0?",
            confirmLabel = "Clear",
            onConfirm = { vm.setQty(p, 0); clearCcFor = null },
            onDismiss = { clearCcFor = null }
        )
    }
}

class UndoUi(val canUndo: Boolean, val canRedo: Boolean, val onUndo: () -> Unit, val onRedo: () -> Unit)

@Composable
fun SearchRow(query: String, onQuery: (String) -> Unit, undo: UndoUi) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SearchField(query, onQuery) }
        IconButton(onClick = undo.onUndo, enabled = undo.canUndo) {
            Text("↶", fontSize = 28.sp, color = if (undo.canUndo) on else off)
        }
        IconButton(onClick = undo.onRedo, enabled = undo.canRedo) {
            Text("↷", fontSize = 28.sp, color = if (undo.canRedo) on else off)
        }
    }
}

@Composable
fun TotalBar(
    total: Double,
    showHistoryButton: Boolean,
    clearLabel: String,
    canSave: Boolean,
    onHistory: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Grand Total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                OdometerText(money(total), fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (showHistoryButton) {
                    Button(onClick = onHistory, enabled = canSave) {
                        Text("History", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                TextButton(onClick = onClear) {
                    Text(clearLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query, onValueChange = onQuery,
        placeholder = { Text("Search Products", fontSize = 16.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Clear search") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun QtyBadge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

@Composable
fun CalculateTab(
    products: List<Product>,
    noProducts: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onTap: (Product) -> Unit,
    undo: UndoUi
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SearchRow(query, onQuery, undo) }
        if (products.isEmpty()) {
            item {
                EmptyHint(
                    if (noProducts) "No products yet.\nTap + to add one, or import rates from the ⋮ menu."
                    else "No products match your search."
                )
            }
        }
        items(products, key = { it.id }) { p ->
            ElevatedCard(
                onClick = { onTap(p) },
                shape = RoundedCornerShape(28.dp),
                colors = cardColors(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Rate: ${money(p.rate)}", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("Total: ${money(Calc.lineTotal(p))}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    QtyBadge("Qty: ${Calc.qtyLabel(p)}")
                }
            }
        }
    }
}

@Composable
fun StockTab(
    products: List<Product>,
    query: String,
    onQuery: (String) -> Unit,
    onEdit: (Product) -> Unit,
    onValue: (Product, Double) -> Unit,
    onClearRequest: (Product) -> Unit,
    undo: UndoUi
) {
    val q = query.trim()
    val items = if (q.isEmpty()) products else products.filter { it.name.contains(q, ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SearchRow(query, onQuery, undo) }
        if (items.isEmpty()) item { EmptyHint("No products found.") }
        items(items, key = { it.id }) { p ->
            StockCard(p, onEdit = { onEdit(p) }, onValue = { onValue(p, it) }, onClear = { onClearRequest(p) })
        }
    }
}

@Composable
fun StockCard(p: Product, onEdit: () -> Unit, onValue: (Double) -> Unit, onClear: () -> Unit) {
    val isPot = Calc.stockIsPot(p)
    val label = if (isPot) "Pot" else "CC"
    val value = Calc.stockValue(p)
    val initial = if (value > 0) num(value) else ""
    var text by remember(p.id) { mutableStateOf(initial) }
    // Keep the field in sync when quantity changes elsewhere (Calculate tab, Clear, edit),
    // but never overwrite what the user is typing.
    LaunchedEffect(p.quantity, p.cc, p.pot, p.soldUnit) {
        val typedQty = text.toDoubleOrNull()?.let { Calc.qtyFromStock(p, it) } ?: 0
        if (typedQty != p.quantity) text = initial
    }
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = cardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit ${p.name}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "Tablets per $label: ${num(if (isPot) p.pot else p.cc)}",
                    fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Total $label: ${String.format(java.util.Locale.US, "%.2f", value)}",
                    fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { s ->
                    val clean = s.filter { c -> c.isDigit() || c == '.' }.take(8)
                    text = clean
                    onValue(clean.toDoubleOrNull() ?: 0.0)
                },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 19.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                modifier = Modifier.width(150.dp)
            )
        }
    }
}
