package id.pondasi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.pondasi.app.pdf.PdfExport
import id.pondasi.core.Report
import id.pondasi.core.ReportLine

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

@Composable
fun ResultScreen(report: Report) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        if (report.allOk) "SEMUA KONTROL AMAN" else "ADA KONTROL YANG TIDAK AMAN",
                        color = if (report.allOk) Green else Red, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    report.checks.forEach { c ->
                        Text("• ${c.label}: ${c.demand} ${c.operator} ${c.capacity}", style = MaterialTheme.typography.bodySmall)
                        Text("   ${c.status}", color = if (c.ok) Green else Red, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { PdfExport.share(ctx, report) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Ekspor laporan PDF")
                    }
                }
            }
        }
        report.sections.forEach { s ->
            item {
                Column {
                    Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    HorizontalDivider()
                }
            }
            s.lines.forEach { l -> item { LineView(l) } }
        }
        item { Text("", Modifier.padding(24.dp)) }
    }
}

@Composable
private fun LineView(l: ReportLine) {
    val small = MaterialTheme.typography.bodySmall
    when (l) {
        is ReportLine.Value -> Column(Modifier.padding(vertical = 2.dp)) {
            if (l.label.isNotEmpty()) Text(l.label, style = small)
            Row {
                Text(if (l.formula.isEmpty()) "" else "${l.formula} = ", style = small, modifier = Modifier.weight(1f))
                Text("${l.value} ${l.unit}".trim(), style = small, fontWeight = FontWeight.Bold)
            }
        }
        is ReportLine.Note -> Text(l.text, style = small, modifier = Modifier.padding(vertical = 2.dp))
        is ReportLine.Check -> Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .background(if (l.ok) Green.copy(alpha = 0.08f) else Red.copy(alpha = 0.08f)).padding(6.dp),
        ) {
            Text(l.label, style = small)
            Text("${l.demand} ${l.operator} ${l.capacity}", style = small)
            Text(l.status, style = small, color = if (l.ok) Green else Red, fontWeight = FontWeight.Bold)
        }
        is ReportLine.Table -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(l.header.joinToString(" | "), style = small, fontWeight = FontWeight.Bold)
            l.rows.forEach { Text(it.joinToString(" | "), style = small) }
        }
    }
}
