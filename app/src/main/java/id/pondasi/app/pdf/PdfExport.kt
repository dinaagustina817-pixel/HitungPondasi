package id.pondasi.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import id.pondasi.core.Report
import id.pondasi.core.ReportLine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menulis laporan perhitungan ke PDF A4 dan membagikannya. */
object PdfExport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val LH = 13f

    fun write(report: Report, file: File) {
        val doc = PdfDocument()
        val normal = Paint().apply { textSize = 9f; isAntiAlias = true }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 13f }
        val green = Paint(bold).apply { color = Color.rgb(46, 125, 50) }
        val red = Paint(bold).apply { color = Color.rgb(198, 40, 40) }
        val gray = Paint(normal).apply { color = Color.GRAY; textSize = 7.5f }

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M
            c.drawText("Hitung Pondasi — ${report.title}", M, H - 20f, gray)
            c.drawText("Hal. $pageNo", W - M - 30f, H - 20f, gray)
        }
        fun ensure(h: Float) { if (y + h > H - M) newPage() }
        fun wrap(text: String, p: Paint, width: Float): List<String> {
            val out = mutableListOf<String>()
            var line = ""
            for (word in text.split(" ")) {
                val t = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(t) > width && line.isNotEmpty()) { out += line; line = word } else line = t
            }
            if (line.isNotEmpty()) out += line
            return out
        }
        fun text(s: String, p: Paint, x: Float = M, width: Float = W - x - M) {
            for (ln in wrap(s, p, width)) { ensure(LH); c.drawText(ln, x, y, p); y += LH }
        }

        newPage()
        y += 4f
        text(report.title, title); y += 2f
        text("Tanggal: " + SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date()), gray)
        text(if (report.allOk) "Kesimpulan: SEMUA KONTROL AMAN" else "Kesimpulan: ADA KONTROL YANG TIDAK AMAN",
            if (report.allOk) green else red)
        y += 6f

        val colLabel = M; val colFormula = M + 200f; val colValue = W - M - 90f
        for (s in report.sections) {
            ensure(LH * 3); y += 6f
            text(s.title, bold)
            c.drawLine(M, y - 9f, W - M, y - 9f, gray); y += 2f
            for (l in s.lines) when (l) {
                is ReportLine.Value -> {
                    val lab = wrap(l.label, normal, colFormula - colLabel - 8f)
                    val fml = wrap(if (l.formula.isEmpty()) "" else "${l.formula} =", normal, colValue - colFormula - 8f)
                    val rows = maxOf(lab.size, fml.size, 1)
                    ensure(LH * rows)
                    for (k in 0 until rows) {
                        lab.getOrNull(k)?.let { c.drawText(it, colLabel, y, normal) }
                        fml.getOrNull(k)?.let { c.drawText(it, colFormula, y, normal) }
                        if (k == 0) c.drawText("${l.value} ${l.unit}".trim(), colValue, y, bold)
                        y += LH
                    }
                }
                is ReportLine.Note -> text(l.text, normal, M + 10f)
                is ReportLine.Check -> {
                    text(l.label, normal)
                    ensure(LH)
                    c.drawText("${l.demand} ${l.operator} ${l.capacity}", colFormula, y, normal)
                    c.drawText(l.status, colValue - 40f, y, if (l.ok) green else red)
                    y += LH + 2f
                }
                is ReportLine.Table -> {
                    val n = l.header.size
                    val cw = (W - 2 * M) / n
                    ensure(LH * 2)
                    l.header.forEachIndexed { k, h -> c.drawText(h, M + k * cw, y, bold) }
                    y += LH
                    for (r in l.rows) {
                        ensure(LH)
                        r.forEachIndexed { k, v -> c.drawText(v, M + k * cw, y, normal) }
                        y += LH
                    }
                }
            }
        }
        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun share(ctx: Context, report: Report) {
        val dir = File(ctx.cacheDir, "laporan").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "Laporan_Pondasi_$stamp.pdf")
        write(report, file)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Bagikan / simpan laporan PDF"))
    }
}
