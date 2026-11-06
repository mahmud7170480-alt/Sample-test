package com.example.ratecalc

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val profileDao = db.profileDao()
    private val productDao = db.productDao()

    private val currentId = MutableStateFlow<Long?>(null)

    val profiles = profileDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currentProfile = combine(profiles, currentId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val products = currentId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else productDao.observe(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _invoices = MutableStateFlow<List<File>>(emptyList())
    val invoices = _invoices.asStateFlow()

    // ---- undo / redo of quantity changes ----
    private val undoStack = ArrayDeque<Map<Long, Int>>()
    private val redoStack = ArrayDeque<Map<Long, Int>>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()
    val canRedo = _canRedo.asStateFlow()
    private var lastEditId: Long? = null
    private var lastEditAt = 0L

    private fun snapshot(): Map<Long, Int> = products.value.associate { it.id to it.quantity }
    private fun flags() { _canUndo.value = undoStack.isNotEmpty(); _canRedo.value = redoStack.isNotEmpty() }
    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        flags()
    }
    private fun resetUndo() { undoStack.clear(); redoStack.clear(); lastEditId = null; flags() }
    private fun applySnapshot(m: Map<Long, Int>) {
        viewModelScope.launch { m.forEach { (id, q) -> productDao.setQty(id, q) } }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot()); lastEditId = null; flags()
        applySnapshot(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot()); lastEditId = null; flags()
        applySnapshot(next)
    }

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (profileDao.count() == 0) createDefaultProfile()
            currentId.value = profileDao.latest()?.id
            refreshInvoices()
        }
    }

    private fun say(msg: String) { _events.tryEmit(msg) }

    /** First run: new profile filled with the bundled rates.csv product list. */
    private suspend fun createDefaultProfile(): Long {
        val id = profileDao.insert(Profile(name = formatDay(System.currentTimeMillis())))
        try {
            val text = getApplication<Application>().assets.open("rates.csv").bufferedReader().use { it.readText() }
            Csv.parse(text).forEach { r ->
                productDao.insert(Product(profileId = id, name = r.name, rate = r.rate, cc = r.cc, pot = r.pot, soldUnit = r.unit))
            }
        } catch (e: Exception) { /* no bundled list: start empty */ }
        return id
    }

    // ---- products ----
    fun addProduct(name: String, rate: Double, cc: Double, pot: Double, unit: String) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            if (productDao.findByName(pid, name) != null) { say("\"$name\" already exists"); return@launch }
            productDao.insert(Product(profileId = pid, name = name, rate = rate, cc = cc, pot = pot, soldUnit = unit))
        }
    }

    fun updateProduct(old: Product, name: String, rate: Double, cc: Double, pot: Double, unit: String) {
        viewModelScope.launch {
            val dup = productDao.findByName(old.profileId, name)
            if (dup != null && dup.id != old.id) { say("\"$name\" already exists"); return@launch }
            productDao.update(old.copy(name = name, rate = rate, cc = cc, pot = pot, soldUnit = unit))
        }
    }

    fun deleteProduct(p: Product) { viewModelScope.launch { productDao.delete(p) } }

    fun setQty(p: Product, q: Int) {
        val newQ = q.coerceIn(0, 999_999)
        if (newQ == p.quantity) return
        val now = System.currentTimeMillis()
        // Typing several digits into one product counts as ONE undo step.
        if (lastEditId != p.id || now - lastEditAt > 2000) pushUndo()
        lastEditId = p.id
        lastEditAt = now
        viewModelScope.launch { productDao.setQty(p.id, newQ) }
    }

    fun clearQuantities() {
        val pid = currentId.value ?: return
        pushUndo()
        lastEditId = null
        viewModelScope.launch { productDao.clearQty(pid) }
    }

    // ---- profiles ----
    /** Sets the current profile's title to the given date (today, or one picked by the user). */
    fun setTitleDate(time: Long) {
        val id = currentId.value ?: return
        val title = formatDay(time)
        viewModelScope.launch {
            profileDao.rename(id, title)
            say("Date set to $title")
        }
    }

    fun switchProfile(id: Long) {
        resetUndo()
        viewModelScope.launch {
            profileDao.touch(id, System.currentTimeMillis())
            currentId.value = id
        }
    }

    /** New profile = fresh calculation with the same product list, quantities at 0. */
    fun createProfile(name: String) {
        resetUndo()
        viewModelScope.launch {
            val source = currentId.value?.let { productDao.all(it) }.orEmpty()
            val id = profileDao.insert(Profile(name = name.ifBlank { formatDay(System.currentTimeMillis()) }))
            source.forEach { productDao.insert(it.copy(id = 0, profileId = id, quantity = 0)) }
            currentId.value = id
        }
    }

    fun renameProfile(id: Long, name: String) { viewModelScope.launch { profileDao.rename(id, name) } }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            profileDao.delete(id) // products cascade
            if (currentId.value == id) {
                resetUndo()
                currentId.value = if (profileDao.count() == 0) createDefaultProfile() else profileDao.latest()?.id
            }
        }
    }

    // ---- history = saved PNG invoices ----
    private suspend fun refreshInvoices() {
        _invoices.value = withContext(Dispatchers.IO) {
            InvoiceImage.dir(getApplication()).listFiles()
                ?.filter { it.extension.equals("png", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }
    }

    fun saveInvoice(items: List<Product>, overwrite: Boolean) {
        if (items.isEmpty()) { say("Nothing to save"); return }
        val willOverwrite = overwrite && _invoices.value.isNotEmpty()
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { InvoiceImage.savePng(getApplication(), items, willOverwrite) }
                refreshInvoices()
                say(if (willOverwrite) "Overwrote ${f.name}" else "Saved ${f.name}")
            } catch (e: Exception) {
                say("Save failed: ${e.message}")
            }
        }
    }

    fun deleteInvoice(f: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { InvoiceImage.deleteInvoice(f) }
            refreshInvoices()
        }
    }

    fun updateInvoiceDate(f: File, time: Long) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { InvoiceImage.updateDate(f, time) }
            refreshInvoices()
            say(if (ok) "Invoice date set to ${formatDay(time)}" else "This invoice has no saved data, date can't be changed")
        }
    }

    fun saveToGallery(f: File) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { InvoiceImage.copyToGallery(getApplication(), f) }
            say(if (ok) "Saved to Pictures/SampleCount" else "Could not save to gallery")
        }
    }

    // ---- backup & restore ----
    private val _pendingRestore = MutableStateFlow<BackupData?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val profilesData = profileDao.allProfiles().map { p ->
                        BackupProfile(
                            p.name, p.createdAt, p.lastAccessed,
                            productDao.all(p.id).map { BackupProduct(it.name, it.rate, it.cc, it.pot, it.soldUnit, it.quantity) }
                        )
                    }
                    val bytes = Backup.write(profilesData, InvoiceImage.dir(getApplication()), System.currentTimeMillis())
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Cannot open file")
                }
                say("Backup saved")
            } catch (e: Exception) {
                say("Backup failed: ${e.message}")
            }
        }
    }

    /** Step 1: read and check the file. Nothing is changed until the user confirms. */
    fun prepareRestore(uri: Uri) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot open file")
                    Backup.read(bytes)
                }
                if (data == null) say("This is not a valid SampleCount backup") else _pendingRestore.value = data
            } catch (e: Exception) {
                say("Could not read backup: ${e.message}")
            }
        }
    }

    fun cancelRestore() { _pendingRestore.value = null }

    /** Step 2: replace ALL current data with the backup. */
    fun confirmRestore() {
        val data = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.withTransaction {
                        profileDao.deleteAll() // products cascade
                        data.profiles.sortedBy { it.lastAccessed }.forEach { p ->
                            val pid = profileDao.insert(Profile(name = p.name, createdAt = p.createdAt, lastAccessed = p.lastAccessed))
                            p.products.forEach {
                                productDao.insert(
                                    Product(
                                        profileId = pid, name = it.name, rate = it.rate, cc = it.cc,
                                        pot = it.pot, soldUnit = it.unit, quantity = it.qty
                                    )
                                )
                            }
                        }
                        if (profileDao.count() == 0) createDefaultProfile()
                        Unit
                    }
                    // Database restored successfully: now replace the saved invoice files.
                    val dir = InvoiceImage.dir(getApplication())
                    dir.listFiles()?.forEach { it.delete() }
                    data.invoiceFiles.forEach { (name, bytes) ->
                        val f = File(dir, name)
                        f.writeBytes(bytes)
                        val t = data.invoiceTimes[name] ?: -1L
                        if (t > 0) f.setLastModified(t)
                    }
                }
                resetUndo()
                currentId.value = profileDao.latest()?.id
                refreshInvoices()
                say("Backup restored")
            } catch (e: Exception) {
                say("Restore failed: ${e.message}")
            }
        }
    }

    // ---- CSV ----
    fun exportTo(uri: Uri) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val csv = Csv.export(productDao.all(pid))
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot open file")
                }
                say("Rates exported")
            } catch (e: Exception) {
                say("Export failed: ${e.message}")
            }
        }
    }

    fun importFrom(uri: Uri) {
        val pid = currentId.value ?: return
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Cannot open file")
                }
                val rows = Csv.parse(text)
                rows.forEach { r ->
                    val existing = productDao.findByName(pid, r.name)
                    if (existing == null) {
                        productDao.insert(Product(profileId = pid, name = r.name, rate = r.rate, cc = r.cc, pot = r.pot, soldUnit = r.unit))
                    } else {
                        productDao.update(existing.copy(rate = r.rate, cc = r.cc, pot = r.pot, soldUnit = r.unit))
                    }
                }
                say("Imported rates for ${rows.size} products")
            } catch (e: Exception) {
                say("Import failed: ${e.message}")
            }
        }
    }
}
