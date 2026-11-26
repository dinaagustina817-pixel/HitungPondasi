package id.pondasi.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.pondasi.core.ConcreteParams
import id.pondasi.core.PileCalculator
import id.pondasi.core.PileCapCalculator
import id.pondasi.core.PileCapInput
import id.pondasi.core.PileInput
import id.pondasi.core.PileType
import id.pondasi.core.Report
import id.pondasi.core.SptCoefficients

/** Nilai awal mengikuti contoh perhitungan pondasi tower crane (Cipete). */
class PileForm {
    val type = mutableStateOf(PileType.BULAT)
    val d = mutableStateOf("0.6"); val t = mutableStateOf("0.1"); val length = mutableStateOf("10")
    val fcPile = mutableStateOf("52"); val nb = mutableStateOf("60"); val e = mutableStateOf("0.2")
    val phiAxial = mutableStateOf("0.6"); val phiLateral = mutableStateOf("0.6")
    val matOverride = mutableStateOf("")
    val kb = mutableStateOf("40"); val ks = mutableStateOf("1"); val kLim = mutableStateOf("380")
    val layers = LayersState(
        listOf(
            LayerState("0", "2", "Lempung", "30", "6"),
            LayerState("2", "4", "Lempung", "55", "12"),
            LayerState("4", "6", "Lempung", "50", "8"),
            LayerState("6", "8", "Lempung", "55", "13"),
            LayerState("8", "10", "Lempung Pasir", "200", "60"),
            LayerState("10", "12", "Pasir kasar gravel", "0", "50"),
            LayerState("12", "14", "Pasir kasar gravel", "0", "60"),
        ),
    )

    // Pilecap
    val fc = mutableStateOf("30"); val fy = mutableStateOf("420")
    val bx = mutableStateOf("2"); val by = mutableStateOf("2")
    val h = mutableStateOf("1.7"); val lx = mutableStateOf("6"); val ly = mutableStateOf("6")
    val z = mutableStateOf("0"); val ws = mutableStateOf("16"); val alphaS = mutableStateOf("40")
    val pu = mutableStateOf("760.95"); val mux = mutableStateOf("931.62"); val muy = mutableStateOf("931.62")
    val hux = mutableStateOf("114.98"); val huy = mutableStateOf("114.98"); val mt = mutableStateOf("3131.48")
    val nx = mutableStateOf("3"); val ny = mutableStateOf("3"); val sx = mutableStateOf("2.4"); val sy = mutableStateOf("2.4")
    val skipCenter = mutableStateOf(true)
    val dPrime = mutableStateOf("0.175"); val barDia = mutableStateOf("25"); val shrinkDia = mutableStateOf("16")
    val sMax = mutableStateOf("200"); val phiFlex = mutableStateOf("0.8"); val beta1 = mutableStateOf("0.85")

    fun setType(tp: PileType) {
        type.value = tp
        val c = SptCoefficients.defaultFor(tp)
        kb.value = "%.2f".format(java.util.Locale.US, c.kb)
        ks.value = "%.2f".format(java.util.Locale.US, c.ks)
        kLim.value = "%.2f".format(java.util.Locale.US, c.kLimit)
    }

    fun calculate(): Report {
        val pile = PileCalculator.calculate(
            PileInput(
                type = type.value, diameter = d.req("D"), wallThickness = t.opt() ?: 0.0, length = length.req("L"),
                fc = fcPile.req("fc' tiang"), layers = layers.toLayers(), nbOverride = nb.opt(),
                spt = SptCoefficients(kb.req("koef. ujung"), ks.req("koef. friksi"), kLim.req("koef. batas")),
                phiAxial = phiAxial.req("φ aksial"), phiLateral = phiLateral.req("φ lateral"),
                eLateral = e.req("e"), materialCapacityOverride = matOverride.opt(),
            ),
        )
        val nxI = nx.req("jumlah tiang arah x").toInt(); val nyI = ny.req("jumlah tiang arah y").toInt()
        if (nxI < 1 || nyI < 1) throw InputError("Jumlah tiang arah x dan y minimal 1")
        val cap = PileCapCalculator.calculate(
            PileCapInput(
                concrete = ConcreteParams(
                    fc = fc.req("fc' pilecap"), fy = fy.req("fy"), beta1 = beta1.req("β1"), phiFlexure = phiFlex.req("φ lentur"),
                    barDia = barDia.req("diameter tulangan"), shrinkBarDia = shrinkDia.req("diameter tulangan susut"), sMax = sMax.req("smax"),
                ),
                bx = bx.req("bx"), by = by.req("by"), h = h.req("h"), lx = lx.req("Lx"), ly = ly.req("Ly"),
                soilAbove = z.opt() ?: 0.0, ws = ws.req("ws"), alphaS = alphaS.req("αs"),
                pu = pu.req("Puk"), mux = mux.opt() ?: 0.0, muy = muy.opt() ?: 0.0,
                hux = hux.opt() ?: 0.0, huy = huy.opt() ?: 0.0, mt = mt.opt() ?: 0.0,
                piles = PileCapInput.grid(nxI, nyI, sx.req("jarak tiang x"), sy.req("jarak tiang y"), skipCenter.value),
                phiPn = pile.phiPn, phiHn = pile.phiHn, dPrime = dPrime.req("d'"),
            ),
        )
        return Report("PERHITUNGAN PONDASI TIANG & PILECAP", pile.report.sections + cap.report.sections)
    }
}

@Composable
fun PileScreen(form: PileForm, onResult: (Report) -> Unit) {
    val error = remember { mutableStateOf<String?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        SectionCard("Jenis & dimensi tiang") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PileType.entries.forEach { tp ->
                    FilterChip(form.type.value == tp, { form.setType(tp) }, label = {
                        Text(when (tp) { PileType.BULAT -> "Bulat"; PileType.PERSEGI -> "Persegi"; PileType.SPUN -> "Spun pile"; PileType.BORE -> "Bore pile" })
                    })
                }
            }
            FieldRow { m ->
                NumField(if (form.type.value == PileType.PERSEGI) "Sisi D" else "Diameter D", form.d, "m", m)
                NumField("Panjang L", form.length, "m", m)
            }
            if (form.type.value == PileType.SPUN) NumField("Tebal dinding t", form.t, "m")
            FieldRow { m -> NumField("fc' tiang", form.fcPile, "MPa", m); NumField("e lateral", form.e, "m", m) }
            NumField("Kapasitas bahan brosur φPn (kosongkan = hitung)", form.matOverride, "kN")
            FieldRow { m -> NumField("φ aksial", form.phiAxial, "", m); NumField("φ lateral", form.phiLateral, "", m) }
        }
        SectionCard("Data tanah SPT") {
            NumField("Nb di dasar tiang (kosongkan = otomatis 8D/4D)", form.nb)
            Text("Koefisien Meyerhof: Qp = kb·Nb·Ab, Qs = ks·Ň·As ≤ kLim·Ň·Ab", style = MaterialTheme.typography.bodySmall)
            FieldRow { m -> NumField("kb", form.kb, "", m); NumField("ks", form.ks, "", m); NumField("kLim", form.kLim, "", m) }
            LayersEditor(form.layers)
        }
        SectionCard("Beban terfaktor") {
            NumField("Gaya aksial kolom Puk", form.pu, "kN")
            FieldRow { m -> NumField("Mux", form.mux, "kNm", m); NumField("Muy", form.muy, "kNm", m) }
            FieldRow { m -> NumField("Hux", form.hux, "kN", m); NumField("Huy", form.huy, "kN", m) }
            NumField("Momen puntir Mt", form.mt, "kNm")
        }
        SectionCard("Susunan tiang") {
            FieldRow { m -> NumField("Jumlah arah x", form.nx, "", m); NumField("Jumlah arah y", form.ny, "", m) }
            FieldRow { m -> NumField("Jarak arah x", form.sx, "m", m); NumField("Jarak arah y", form.sy, "m", m) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(form.skipCenter.value, { form.skipCenter.value = it })
                Text("Tanpa tiang di tengah")
            }
        }
        SectionCard("Pilecap") {
            FieldRow { m -> NumField("Kolom bx", form.bx, "m", m); NumField("Kolom by", form.by, "m", m) }
            FieldRow { m -> NumField("Lx", form.lx, "m", m); NumField("Ly", form.ly, "m", m) }
            FieldRow { m -> NumField("Tebal h", form.h, "m", m); NumField("d'", form.dPrime, "m", m) }
            FieldRow { m -> NumField("Tanah di atas z", form.z, "m", m); NumField("γ tanah", form.ws, "kN/m³", m) }
            FieldRow { m -> NumField("fc'", form.fc, "MPa", m); NumField("fy", form.fy, "MPa", m) }
            FieldRow { m -> NumField("Tulangan D", form.barDia, "mm", m); NumField("Susut Ø", form.shrinkDia, "mm", m) }
            FieldRow { m -> NumField("smax", form.sMax, "mm", m); NumField("αs", form.alphaS, "", m) }
            FieldRow { m -> NumField("φ lentur", form.phiFlex, "", m); NumField("β1", form.beta1, "", m) }
        }
        error.value?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        Button(
            onClick = {
                error.value = null
                try { onResult(form.calculate()) } catch (e: Exception) { error.value = e.message }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("Hitung") }
    }
}
