package com.example.ratecalc

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceImage {
    private fun fit(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Invoice layout: title, date, Item/Rate/Qty/Total table, Grand Total. */
    fun render(items: List<Product>, time: Long): Bitmap {
        val w = 1000
        val pad = 50f
        val right = w - pad
        val xRate = 550f
        val xQty = 750f
        val rowH = 60f
        val headerY = 285f
        val lineY = headerY + 53f
        val firstY = lineY + 46f
        val lastY = firstY + rowH * (maxOf(items.size, 1) - 1)
        val gLineY = lastY + 93f
        val totalY = gLineY + 44f
        val h = (totalY + 70f).toInt()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 3f }

        // Title + date
        p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.color = Color.parseColor("#111111")
        p.textSize = 84f
        p.textAlign = Paint.Align.CENTER
        c.drawText("INVOICE", w / 2f, 135f, p)
        p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        p.color = Color.parseColor("#616161")
        p.textSize = 32f
        c.drawText(formatDay(time), w / 2f, 195f, p)

        // Header
        p.textSize = 38f
        p.textAlign = Paint.Align.LEFT
        c.drawText("Item", pad, headerY, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("Rate", xRate, headerY, p)
        c.drawText("Qty", xQty, headerY, p)
        c.drawText("Total", right, headerY, p)
        c.drawLine(pad, lineY, right, lineY, line)

        // Rows
        p.color = Color.parseColor("#111111")
        var y = firstY
        items.forEach { it ->
            p.textAlign = Paint.Align.LEFT
            c.drawText(fit(it.name, p, 330f), pad, y, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText(money(it.rate), xRate, y, p)
            c.drawText(Calc.qtyLabel(it), xQty, y, p)
            c.drawText(money(Calc.lineTotal(it)), right, y, p)
            y += rowH
        }

        // Grand total
        c.drawLine(pad, gLineY, right, gLineY, line)
        p.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        p.textSize = 50f
        p.textAlign = Paint.Align.RIGHT
        c.drawText("Grand Total: " + money(items.sumOf { Calc.lineTotal(it) }), right, totalY, p)
        return bmp
    }

    /** Folder that holds the saved history PNGs. */
    fun dir(ctx: Context): File = File(ctx.filesDir, "invoices").apply { mkdirs() }

    private fun metaFile(png: File) = File(png.parentFile, png.nameWithoutExtension + ".json")

    // Each saved PNG has a small .json next to it so the date can be updated later.
    private fun writeMeta(png: File, items: List<Product>, time: Long) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("n", it.name).put("r", it.rate).put("q", it.quantity).put("u", it.soldUnit))
        }
        metaFile(png).writeText(JSONObject().put("time", time).put("items", arr).toString())
    }

    private fun readMeta(png: File): Pair<Long, List<Product>>? {
        val f = metaFile(png)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("items")
            val list = (0 until arr.length()).map {
                val x = arr.getJSONObject(it)
                Product(
                    profileId = 0, name = x.getString("n"), rate = x.getDouble("r"),
                    soldUnit = x.getString("u"), quantity = x.getInt("q")
                )
            }
            o.getLong("time") to list
        } catch (e: Exception) {
            null
        }
    }

    /** Re-renders a saved invoice with a new date. Returns false for invoices without saved data. */
    fun updateDate(png: File, time: Long): Boolean {
        val meta = readMeta(png) ?: return false
        FileOutputStream(png).use { render(meta.second, time).compress(Bitmap.CompressFormat.PNG, 100, it) }
        writeMeta(png, meta.second, time)
        png.setLastModified(System.currentTimeMillis())
        return true
    }

    fun deleteInvoice(png: File) {
        png.delete()
        metaFile(png).delete()
    }

    /** Saves Invoice_yyyyMMdd_HHmmss.png (or overwrites the latest one) in the history folder. */
    fun savePng(ctx: Context, items: List<Product>, overwriteLatest: Boolean = false): File {
        val latest = if (overwriteLatest) {
            dir(ctx).listFiles()?.filter { it.extension.equals("png", true) }?.maxByOrNull { it.lastModified() }
        } else null
        val now = System.currentTimeMillis()
        val f = latest ?: File(
            dir(ctx),
            "Invoice_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now)) + ".png"
        )
        FileOutputStream(f).use { render(items, now).compress(Bitmap.CompressFormat.PNG, 100, it) }
        writeMeta(f, items, now)
        f.setLastModified(now)
        if (latest == null) {
            try { copyToGallery(ctx, f) } catch (e: Exception) { /* history copy is what matters */ }
        }
        return f
    }

    /** Copies a PNG into Pictures/SampleCount (Android 10+; no permission needed). */
    fun copyToGallery(ctx: Context, f: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SampleCount")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }

    fun shareFile(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share invoice"))
    }

    fun share(ctx: Context, items: List<Product>) {
        val bmp = render(items, System.currentTimeMillis())
        val dir = File(ctx.cacheDir, "invoices").apply { mkdirs() }
        val f = File(dir, "invoice.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share invoice"))
    }
}
