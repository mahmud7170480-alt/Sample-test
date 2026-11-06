package com.example.ratecalc

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupProduct(
    val name: String, val rate: Double, val cc: Double, val pot: Double, val unit: String, val qty: Int
)

data class BackupProfile(
    val name: String, val createdAt: Long, val lastAccessed: Long, val products: List<BackupProduct>
)

/** Everything read from a backup file, held in memory until the user confirms the restore. */
class BackupData(
    val createdAt: Long,
    val profiles: List<BackupProfile>,
    val invoiceFiles: Map<String, ByteArray>,
    val invoiceTimes: Map<String, Long>
) {
    val productCount: Int get() = profiles.sumOf { it.products.size }
    val invoiceCount: Int get() = invoiceFiles.keys.count { it.endsWith(".png", ignoreCase = true) }
}

/**
 * Backup = one .zip file:
 *   backup.json            profiles + products (rates, CC, Pot, sold-as, quantities)
 *   invoices/<name>.png    every saved History invoice
 *   invoices/<name>.json   the data that lets you change an invoice's date later
 */
object Backup {
    private const val VERSION = 1

    fun fileName(now: Long): String =
        "SampleCount-Backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now)) + ".zip"

    fun write(profiles: List<BackupProfile>, invoiceDir: File, now: Long): ByteArray {
        val profileArray = JSONArray()
        profiles.forEach { p ->
            val prods = JSONArray()
            p.products.forEach {
                prods.put(
                    JSONObject().put("n", it.name).put("r", it.rate).put("c", it.cc)
                        .put("p", it.pot).put("u", it.unit).put("q", it.qty)
                )
            }
            profileArray.put(
                JSONObject().put("name", p.name).put("createdAt", p.createdAt)
                    .put("lastAccessed", p.lastAccessed).put("products", prods)
            )
        }
        val root = JSONObject().put("app", "SampleCount").put("version", VERSION)
            .put("createdAt", now).put("profiles", profileArray)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(root.toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
            invoiceDir.listFiles()
                ?.filter { it.isFile && (it.extension.equals("png", true) || it.extension.equals("json", true)) }
                ?.forEach { f ->
                    val e = ZipEntry("invoices/" + f.name)
                    e.time = f.lastModified()
                    z.putNextEntry(e)
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
        }
        return out.toByteArray()
    }

    /** Returns null when the file is not a valid SampleCount backup. Nothing is changed by reading. */
    fun read(bytes: ByteArray): BackupData? {
        try {
            var json: String? = null
            val files = LinkedHashMap<String, ByteArray>()
            val times = HashMap<String, Long>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val data = z.readBytes()
                        val name = e.name
                        if (name == "backup.json") {
                            json = data.toString(Charsets.UTF_8)
                        } else if (name.startsWith("invoices/")) {
                            val fn = name.removePrefix("invoices/")
                            val plain = fn.isNotEmpty() && !fn.contains('/') && !fn.contains('\\') && !fn.contains("..")
                            val okType = fn.endsWith(".png", true) || fn.endsWith(".json", true)
                            if (plain && okType) {
                                files[fn] = data
                                times[fn] = e.time
                            }
                        }
                    }
                    e = z.nextEntry
                }
            }
            val text = json ?: return null
            val o = JSONObject(text)
            if (o.optString("app") != "SampleCount") return null
            val pa = o.getJSONArray("profiles")
            val profiles = (0 until pa.length()).map { i ->
                val p = pa.getJSONObject(i)
                val arr = p.getJSONArray("products")
                BackupProfile(
                    name = p.getString("name"),
                    createdAt = p.optLong("createdAt", 0L),
                    lastAccessed = p.optLong("lastAccessed", 0L),
                    products = (0 until arr.length()).map { k ->
                        val x = arr.getJSONObject(k)
                        BackupProduct(
                            name = x.getString("n"),
                            rate = x.getDouble("r"),
                            cc = x.optDouble("c", 0.0),
                            pot = x.optDouble("p", 0.0),
                            unit = x.optString("u", "Normal"),
                            qty = x.optInt("q", 0)
                        )
                    }
                )
            }
            return BackupData(o.optLong("createdAt", 0L), profiles, files, times)
        } catch (e: Exception) {
            return null
        }
    }
}
