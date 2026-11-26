package id.pondasi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.pondasi.core.BearingSource
import id.pondasi.core.ConcreteParams
import id.pondasi.core.FootplateCalculator
import id.pondasi.core.FootplateInput
import id.pondasi.core.Report
import id.pondasi.core.fmt

class FootplateForm {
    val fc = mutableStateOf("25"); val fy = mutableStateOf("420"); val wc = mutableStateOf("24")
    val bx = mutableStateOf("0.4"); val by = mutableStateOf("0.4")
    val lx = mutableStateOf("2.0"); val ly = mutableStateOf("2.0"); val h = mutableStateOf("0.5")
    val df = mutableStateOf("1.5"); val ws = mutableStateOf("17"); val alphaS = mutableStateOf("40")
    val pu = mutableStateOf("1000"); val mux = mutableStateOf("50"); val muy = mutableStateOf("0")
    val useSpt = mutableStateOf(true)
    val qa = mutableStateOf("200"); val nOverride = mutableStateOf("")
    val dPrime = mutableStateOf("0.075"); val barDia = mutableStateOf("16"); val sMax = mutableStateOf("200")
    val phiFlex = mutableStateOf("0.9"); val beta1 = mutableStateOf("0.85"); val rhoMin = mutableStateOf("0.0018")
    val layers = LayersState(
        listOf(
            LayerState("0", "2", "Lempung", "30", "8"),
            LayerState("2", "4", "Lempung", "50", "12"),
            LayerState("4", "6", "Pasir", "0", "20"),
        ),
    )

    fun toInput(): FootplateInput = FootplateInput(
        concrete = ConcreteParams(
            fc = fc.req("fc'"), fy = fy.req("fy"), wc = wc.req("wc"),
            beta1 = beta1.req("β1"), phiFlexure = phiFlex.req("φ lentur"), rhoMin = rhoMin.req("ρmin"),
            barDia = barDia.req("diameter tulangan"), sMax = sMax.req("smax"),
        ),
        bx = bx.req("bx"), by = by.req("by"), lx = lx.req("Lx"), ly = ly.req("Ly"), h = h.req("h"),
        df = df.req("Df"), ws = ws.req("ws"), alphaS = alphaS.req("αs"),
        pu = pu.req("Pu"), mux = mux.opt() ?: 0.0, muy = muy.opt() ?: 0.0,
        bearing = if (useSpt.value) BearingSource.Spt(layers.toLayers(), nOverride.opt()) else BearingSource.Manual(qa.req("qa")),
        dPrime = dPrime.req("d'"),
    )
}

@Composable
fun FootplateScreen(form: FootplateForm, onResult: (Report) -> Unit) {
    val error = remember { mutableStateOf<String?>(null) }
    val info = remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        SectionCard("Beban terfaktor") {
            NumField("Gaya aksial Pu", form.pu, "kN")
            FieldRow { m -> NumField("Mux", form.mux, "kNm", m); NumField("Muy", form.muy, "kNm", m) }
        }
        SectionCard("Dimensi") {
            FieldRow { m -> NumField("Kolom bx", form.bx, "m", m); NumField("Kolom by", form.by, "m", m) }
            FieldRow { m -> NumField("Pondasi Lx", form.lx, "m", m); NumField("Pondasi Ly", form.ly, "m", m) }
            FieldRow { m -> NumField("Tebal h", form.h, "m", m); NumField("Kedalaman Df", form.df, "m", m) }
            FieldRow { m -> NumField("γ tanah", form.ws, "kN/m³", m); NumField("αs (40/30/20)", form.alphaS, "", m) }
        }
        SectionCard("Daya dukung tanah") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(form.useSpt.value, { form.useSpt.value = true }, label = { Text("Dari data SPT") })
                FilterChip(!form.useSpt.value, { form.useSpt.value = false }, label = { Text("qa manual") })
            }
            if (form.useSpt.value) {
                NumField("N rata-rata (kosongkan = otomatis)", form.nOverride)
                LayersEditor(form.layers)
            } else {
                NumField("Daya dukung izin qa", form.qa, "kN/m²")
            }
        }
        SectionCard("Bahan & tulangan") {
            FieldRow { m -> NumField("fc'", form.fc, "MPa", m); NumField("fy", form.fy, "MPa", m) }
            FieldRow { m -> NumField("wc", form.wc, "kN/m³", m); NumField("d'", form.dPrime, "m", m) }
            FieldRow { m -> NumField("Diameter tulangan", form.barDia, "mm", m); NumField("smax", form.sMax, "mm", m) }
            FieldRow { m -> NumField("φ lentur", form.phiFlex, "", m); NumField("β1", form.beta1, "", m) }
            NumField("ρmin", form.rhoMin)
        }
        error.value?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        info.value?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp)) }
        OutlinedButton(
            onClick = {
                error.value = null; info.value = null
                try {
                    val s = FootplateCalculator.autoSize(form.toInput())
                    if (s == null) error.value = "Tidak ditemukan ukuran yang memenuhi (maks. 8 m × 1.5 m)."
                    else {
                        form.lx.value = fmt(s.lx); form.ly.value = fmt(s.ly); form.h.value = fmt(s.h)
                        info.value = "Ukuran minimum: ${fmt(s.lx)} × ${fmt(s.ly)} m, tebal ${fmt(s.h)} m"
                    }
                } catch (e: Exception) { error.value = e.message }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cari ukuran minimum otomatis") }
        Button(
            onClick = {
                error.value = null
                try { onResult(FootplateCalculator.calculate(form.toInput()).report) } catch (e: Exception) { error.value = e.message }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("Hitung") }
    }
}
