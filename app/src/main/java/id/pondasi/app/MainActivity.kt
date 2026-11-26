package id.pondasi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.pondasi.app.ui.FootplateForm
import id.pondasi.app.ui.FootplateScreen
import id.pondasi.app.ui.PileForm
import id.pondasi.app.ui.PileScreen
import id.pondasi.app.ui.ResultScreen
import id.pondasi.core.Report

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Footplate : Screen
    data object Pile : Screen
    data class Result(val report: Report, val from: Screen) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val footForm = remember { FootplateForm() }
    val pileForm = remember { PileForm() }
    val back: () -> Unit = {
        screen = when (val s = screen) {
            is Screen.Result -> s.from
            else -> Screen.Home
        }
    }
    BackHandler(enabled = screen != Screen.Home, onBack = back)

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(when (screen) {
                    Screen.Home -> "Hitung Pondasi"
                    Screen.Footplate -> "Pondasi Telapak"
                    Screen.Pile -> "Tiang Pancang & Pilecap"
                    is Screen.Result -> "Hasil Perhitungan"
                })
            },
            navigationIcon = {
                if (screen != Screen.Home) IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = screen) {
                Screen.Home -> Home(onFootplate = { screen = Screen.Footplate }, onPile = { screen = Screen.Pile })
                Screen.Footplate -> FootplateScreen(footForm) { screen = Screen.Result(it, Screen.Footplate) }
                Screen.Pile -> PileScreen(pileForm) { screen = Screen.Result(it, Screen.Pile) }
                is Screen.Result -> ResultScreen(s.report)
            }
        }
    }
}

@Composable
private fun Home(onFootplate: () -> Unit, onPile: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        MenuCard("Pondasi Telapak (Footplate)",
            "Daya dukung tanah dari SPT, kontrol tegangan, geser satu arah, geser pons, tulangan lentur, ukuran otomatis.",
            onFootplate)
        MenuCard("Tiang Pancang & Pilecap",
            "Bulat, persegi, spun pile, bore pile. Daya dukung aksial (bahan & SPT Meyerhof), lateral (Brinch Hansen), kontrol pilecap.",
            onPile)
        Text(
            "Acuan: SNI 2847 (beton) dan SNI 8460 (geoteknik). Hasil perhitungan wajib diperiksa oleh perencana yang berwenang.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun MenuCard(title: String, desc: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
