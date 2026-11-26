package id.pondasi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.pondasi.core.SoilLayer

/** Mengubah teks menjadi angka; menerima koma sebagai pemisah desimal. */
fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()

class InputError(message: String) : Exception(message)

fun MutableState<String>.req(name: String): Double =
    value.num() ?: throw InputError("Nilai \"$name\" belum diisi atau tidak valid")

fun MutableState<String>.opt(): Double? = value.trim().takeIf { it.isNotEmpty() }?.num()

@Composable
fun NumField(label: String, state: MutableState<String>, unit: String = "", modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(if (unit.isEmpty()) label else "$label ($unit)") },
        singleLine = true,
        isError = state.value.isNotBlank() && state.value.num() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.padding(vertical = 2.dp),
    )
}

@Composable
fun FieldRow(content: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        content(Modifier.weight(1f))
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Satu baris lapisan tanah yang dapat diedit. */
class LayerState(z1: String, z2: String, desc: String, cu: String, n: String) {
    val z1 = mutableStateOf(z1)
    val z2 = mutableStateOf(z2)
    val desc = mutableStateOf(desc)
    val cu = mutableStateOf(cu)
    val n = mutableStateOf(n)

    fun toLayer(no: Int) = SoilLayer(
        z1.req("z1 lapisan $no"), z2.req("z2 lapisan $no"), desc.value,
        cu.opt() ?: 0.0, n.req("N-SPT lapisan $no"),
    )
}

class LayersState(initial: List<LayerState>) {
    val rows = mutableStateListOf<LayerState>().apply { addAll(initial) }
    fun toLayers(): List<SoilLayer> {
        if (rows.isEmpty()) throw InputError("Data tanah SPT belum diisi")
        return rows.mapIndexed { i, r -> r.toLayer(i + 1) }
    }
}

@Composable
fun LayersEditor(state: LayersState) {
    state.rows.forEachIndexed { i, r ->
        Text("Lapisan ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        FieldRow { m -> NumField("z1", r.z1, "m", m); NumField("z2", r.z2, "m", m) }
        OutlinedTextField(r.desc.value, { r.desc.value = it }, label = { Text("Jenis tanah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldRow { m -> NumField("cu", r.cu, "kN/m²", m); NumField("N-SPT", r.n, "", m) }
        TextButton(onClick = { state.rows.removeAt(i) }) { Text("Hapus lapisan ${i + 1}") }
    }
    TextButton(onClick = {
        val last = state.rows.lastOrNull()
        val top = last?.z2?.value ?: "0"
        val bottom = top.num()?.let { (it + 2.0).toString() } ?: ""
        state.rows.add(LayerState(top, bottom, last?.desc?.value ?: "Lempung", "0", ""))
    }) { Text("+ Tambah lapisan") }
}
