package id.pondasi.core

import java.util.Locale

/** Satu baris pada laporan perhitungan. */
sealed interface ReportLine {
    /** Baris nilai: uraian, rumus/simbol, nilai, satuan. */
    data class Value(
        val label: String,
        val formula: String,
        val value: String,
        val unit: String = "",
    ) : ReportLine

    /** Baris teks bebas (keterangan, rumus umum). */
    data class Note(val text: String) : ReportLine

    /** Baris kontrol: syarat dan statusnya. */
    data class Check(
        val label: String,
        val demand: String,
        val operator: String,
        val capacity: String,
        val ok: Boolean,
        val okText: String = "AMAN (OK)",
        val ngText: String = "TIDAK AMAN (NG)",
    ) : ReportLine {
        val status: String get() = if (ok) okText else ngText
    }

    /** Tabel sederhana (mis. data tanah, koordinat tiang). */
    data class Table(val header: List<String>, val rows: List<List<String>>) : ReportLine
}

data class ReportSection(val title: String, val lines: List<ReportLine>)

data class Report(val title: String, val sections: List<ReportSection>) {
    val checks: List<ReportLine.Check>
        get() = sections.flatMap { s -> s.lines.filterIsInstance<ReportLine.Check>() }

    val allOk: Boolean get() = checks.all { it.ok }

    /** Laporan dalam bentuk teks polos (untuk dibagikan / diuji). */
    fun toPlainText(): String = buildString {
        appendLine(title)
        appendLine("=".repeat(title.length))
        for (s in sections) {
            appendLine()
            appendLine(s.title)
            for (l in s.lines) when (l) {
                is ReportLine.Value -> appendLine("  ${l.label}: ${l.formula} = ${l.value} ${l.unit}".trimEnd())
                is ReportLine.Note -> appendLine("  ${l.text}")
                is ReportLine.Check -> appendLine("  ${l.label}: ${l.demand} ${l.operator} ${l.capacity} → ${l.status}")
                is ReportLine.Table -> {
                    appendLine("  " + l.header.joinToString(" | "))
                    l.rows.forEach { appendLine("  " + it.joinToString(" | ")) }
                }
            }
        }
    }
}

internal class SectionBuilder(private val title: String) {
    val lines = mutableListOf<ReportLine>()

    fun value(label: String, formula: String, v: Double, unit: String = "", digits: Int = 2): Double {
        lines += ReportLine.Value(label, formula, fmt(v, digits), unit)
        return v
    }

    fun text(label: String, formula: String, v: String, unit: String = "") {
        lines += ReportLine.Value(label, formula, v, unit)
    }

    fun note(t: String) { lines += ReportLine.Note(t) }

    fun check(
        label: String, demand: Double, op: String, capacity: Double, ok: Boolean, unit: String = "",
        okText: String = "AMAN (OK)", ngText: String = "TIDAK AMAN (NG)", digits: Int = 2,
    ): Boolean {
        val u = if (unit.isEmpty()) "" else " $unit"
        lines += ReportLine.Check(label, fmt(demand, digits) + u, op, fmt(capacity, digits) + u, ok, okText, ngText)
        return ok
    }

    fun table(header: List<String>, rows: List<List<String>>) { lines += ReportLine.Table(header, rows) }

    fun build() = ReportSection(title, lines.toList())
}

internal class ReportBuilder(private val title: String) {
    private val sections = mutableListOf<ReportSection>()
    fun section(title: String, block: SectionBuilder.() -> Unit) {
        sections += SectionBuilder(title).apply(block).build()
    }
    fun build() = Report(title, sections.toList())
}

fun fmt(v: Double, digits: Int = 2): String = String.format(Locale.US, "%,.${digits}f", v)
