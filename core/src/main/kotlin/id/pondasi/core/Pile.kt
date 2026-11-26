package id.pondasi.core

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class PileType(val label: String, val driven: Boolean) {
    BULAT("Tiang pancang beton bulat (pejal)", true),
    PERSEGI("Tiang pancang beton persegi", true),
    SPUN("Spun pile (bulat berongga)", true),
    BORE("Bore pile (tiang bor)", false),
}

/** Lapisan tanah hasil uji SPT. */
data class SoilLayer(
    val top: Double,          // z1, m
    val bottom: Double,       // z2, m
    val description: String,  // jenis tanah
    val cu: Double,           // kohesi undrained, kN/m2 (0 untuk pasir)
    val nSpt: Double,         // N-SPT
)

/** Koefisien metode Meyerhof dari SPT: Qp = kb·Nb·Ab, Qs = ks·Ň·As, Qp+Qs ≤ kLimit·Ň·Ab. */
data class SptCoefficients(val kb: Double, val ks: Double, val kLimit: Double) {
    companion object {
        /** Tiang pancang (perpindahan besar). */
        val DRIVEN = SptCoefficients(kb = 40.0, ks = 1.0, kLimit = 380.0)
        /** Tiang bor: tahanan ujung ≈ 1/3 dan friksi ≈ 1/2 tiang pancang (Meyerhof 1976). */
        val BORED = SptCoefficients(kb = 40.0 / 3.0, ks = 0.5, kLimit = 380.0 / 3.0)
        fun defaultFor(t: PileType) = if (t.driven) DRIVEN else BORED
    }
}

data class PileInput(
    val type: PileType = PileType.BULAT,
    val diameter: Double = 0.6,         // m (sisi untuk persegi)
    val wallThickness: Double = 0.1,    // m, hanya spun pile
    val length: Double = 10.0,          // m
    val fc: Double = 52.0,              // MPa
    val wc: Double = 24.0,              // kN/m3
    val layers: List<SoilLayer>,
    /** Nb manual; null = dihitung rata-rata 8D di atas s.d. 4D di bawah dasar tiang. */
    val nbOverride: Double? = null,
    val spt: SptCoefficients = SptCoefficients.defaultFor(type),
    val phiAxial: Double = 0.6,
    val phiLateral: Double = 0.6,
    /** Tinggi gaya lateral di atas tanah, m. */
    val eLateral: Double = 0.2,
    /** Kapasitas bahan dari brosur pabrik (kN, sudah termasuk φ); null = dihitung 0.30·fc'·A. */
    val materialCapacityOverride: Double? = null,
    /** Pembulatan ke bawah tahanan aksial pakai, kN (0 = tanpa pembulatan). */
    val roundDownTo: Double = 10.0,
)

data class PileGeometry(
    val endArea: Double,       // Ab, m2 (spun pile dianggap tersumbat)
    val materialArea: Double,  // A beton, m2
    val perimeter: Double,     // m
    val sectionModulus: Double // W, m3
)

data class PileResult(
    val geometry: PileGeometry,
    val nAvg: Double, val nb: Double,
    val qp: Double, val qs: Double,
    val phiPnMaterial: Double, val phiPnSpt: Double,
    val phiPn: Double,            // dipakai (sudah dibulatkan)
    val my: Double, val cAvg: Double,
    val phiHn: Double,
    val longPile: Boolean,
    val report: Report,
)

object PileCalculator {

    fun geometry(i: PileInput): PileGeometry {
        val d = i.diameter
        return when (i.type) {
            PileType.BULAT, PileType.BORE -> PileGeometry(PI / 4 * d * d, PI / 4 * d * d, PI * d, PI * d * d * d / 32)
            PileType.PERSEGI -> PileGeometry(d * d, d * d, 4 * d, d * d * d / 6)
            PileType.SPUN -> {
                val di = (d - 2 * i.wallThickness).coerceAtLeast(0.0)
                PileGeometry(PI / 4 * d * d, PI / 4 * (d * d - di * di), PI * d, PI * (d * d * d * d - di * di * di * di) / (32 * d))
            }
        }
    }

    /** Panjang bagian lapisan yang berada sepanjang tiang. */
    private fun li(l: SoilLayer, length: Double) = max(0.0, min(l.bottom, length) - l.top)

    /** Rata-rata N-SPT berbobot tebal pada rentang [z1, z2]. */
    fun averageN(layers: List<SoilLayer>, z1: Double, z2: Double): Double {
        var sum = 0.0; var t = 0.0
        for (l in layers) {
            val h = max(0.0, min(l.bottom, z2) - max(l.top, z1))
            sum += h * l.nSpt; t += h
        }
        return if (t > 0) sum / t else 0.0
    }

    fun calculate(i: PileInput): PileResult {
        val rb = ReportBuilder("PERHITUNGAN DAYA DUKUNG TIANG (${i.type.label})")
        val g = geometry(i)
        val dimLabel = if (i.type == PileType.PERSEGI) "Sisi tiang" else "Diameter tiang"

        rb.section("A. DATA BAHAN") {
            note("Jenis tiang : ${i.type.label}")
            value(dimLabel, "D", i.diameter, "m", 3)
            if (i.type == PileType.SPUN) value("Tebal dinding spun pile", "t", i.wallThickness, "m", 3)
            value("Panjang tiang", "L", i.length, "m")
            value("Kuat tekan beton tiang", "fc'", i.fc, "MPa", 1)
            value("Berat beton bertulang", "wc", i.wc, "kN/m³", 1)
        }

        var sumLi = 0.0; var sumLiN = 0.0; var sumCuLi = 0.0
        val rows = i.layers.mapIndexed { idx, l ->
            val h = li(l, i.length)
            sumLi += h; sumLiN += h * l.nSpt; sumCuLi += h * l.cu
            listOf("${idx + 1}", fmt(l.top), fmt(l.bottom), l.description, fmt(l.cu, 0), fmt(l.nSpt, 0), fmt(h), fmt(h * l.nSpt), fmt(h * l.cu))
        }
        rb.section("B. DATA TANAH (SPT)") {
            table(listOf("No", "z1 (m)", "z2 (m)", "Jenis tanah", "cu (kN/m²)", "N-SPT", "Li (m)", "Li·N", "cu·Li"), rows)
            text("Jumlah", "ΣLi ; ΣLi·N ; Σcu·Li", "${fmt(sumLi)} ; ${fmt(sumLiN)} ; ${fmt(sumCuLi)}")
            if (sumLi + 1e-9 < i.length) note("PERINGATAN: data tanah hanya sampai ${fmt(sumLi)} m, lebih pendek dari panjang tiang.")
        }

        // 1. Kekuatan bahan
        var phiPnMat = 0.0
        rb.section("C.1 TAHANAN AKSIAL BERDASARKAN KEKUATAN BAHAN") {
            if (i.materialCapacityOverride != null) {
                phiPnMat = value("Tahanan aksial dari brosur pabrik", "φ · Pn", i.materialCapacityOverride, "kN")
            } else {
                val a = value("Luas penampang beton", if (i.type == PileType.PERSEGI) "A = D²" else if (i.type == PileType.SPUN) "A = π/4 · (D² − Di²)" else "A = π/4 · D²", g.materialArea, "m²", 4)
                val wp = value("Berat tiang", "Wp = A · L · wc", a * i.length * i.wc, "kN")
                val fcK = value("Kuat tekan beton", "fc'", i.fc * 1000, "kPa", 0)
                val pn = value("Kapasitas dukung nominal", "Pn = 0.30 · fc' · A − 1.2 · Wp", 0.3 * fcK * a - 1.2 * wp, "kN")
                value("Faktor reduksi kekuatan", "φ", i.phiAxial)
                phiPnMat = value("Tahanan aksial tiang", "φ · Pn", i.phiAxial * pn, "kN")
            }
        }

        // 2. SPT Meyerhof
        val c = i.spt
        val nAvg = if (sumLi > 0) sumLiN / sumLi else 0.0
        val nbAuto = averageN(i.layers, i.length - 8 * i.diameter, i.length + 4 * i.diameter)
        val nb = i.nbOverride ?: nbAuto
        var qp = 0.0; var qs = 0.0; var phiPnSpt = 0.0
        rb.section("C.2 TAHANAN AKSIAL BERDASARKAN UJI SPT (MEYERHOF)") {
            note("Pn = ${fmt(c.kb, 2)} · Nb · Ab + ${fmt(c.ks, 2)} · Ň · As  (kN)   dan harus ≤ ${fmt(c.kLimit, 2)} · Ň · Ab  (kN)")
            note("Nb = nilai SPT di sekitar dasar tiang (8D di atas s.d. 4D di bawah dasar tiang); Ň = SPT rata-rata sepanjang tiang")
            value("Nilai SPT rata-rata sepanjang tiang", "Ň = Σ(Li·N) / ΣLi", nAvg)
            value("Nilai SPT di sekitar dasar tiang", if (i.nbOverride != null) "Nb (input)" else "Nb = rata-rata (L−8D … L+4D)", nb)
            val ab = value("Luas dasar tiang", if (i.type == PileType.PERSEGI) "Ab = D²" else "Ab = π/4 · D²", g.endArea, "m²", 4)
            val asf = value("Luas selimut tiang", if (i.type == PileType.PERSEGI) "As = 4 · D · L" else "As = π · D · L", g.perimeter * i.length, "m²", 4)
            qp = value("Tahanan ultimit ujung tiang", "Qp = ${fmt(c.kb, 2)} · Nb · Ab", c.kb * nb * ab, "kN")
            qs = value("Tahanan ultimit friksi tiang", "Qs = ${fmt(c.ks, 2)} · Ň · As", c.ks * nAvg * asf, "kN")
            val pn0 = value("", "Pn = Qp + Qs", qp + qs, "kN")
            val lim = value("Batas atas", "${fmt(c.kLimit, 2)} · Ň · Ab", c.kLimit * nAvg * ab, "kN")
            val pn = value("Kapasitas nominal tiang", "Pn = min(Pn ; batas atas)", min(pn0, lim), "kN")
            value("Faktor reduksi kekuatan", "φ", i.phiAxial)
            phiPnSpt = value("Tahanan aksial tiang", "φ · Pn", i.phiAxial * pn, "kN")
        }

        val phiMin = min(phiPnMat, phiPnSpt)
        val phiPn = if (i.roundDownTo > 0) floor(phiMin / i.roundDownTo) * i.roundDownTo else phiMin
        rb.section("C.3 REKAP TAHANAN AKSIAL TIANG") {
            value("Berdasarkan kekuatan bahan", "φ · Pn", phiPnMat, "kN")
            value("Berdasarkan uji SPT (Meyerhof)", "φ · Pn", phiPnSpt, "kN")
            value("Daya dukung aksial terkecil", "φ · Pn", phiMin, "kN")
            value(if (i.roundDownTo > 0) "Diambil tahanan aksial (dibulatkan ke bawah ${fmt(i.roundDownTo, 0)} kN)" else "Diambil tahanan aksial", "φ · Pn", phiPn, "kN")
        }

        // Lateral Brinch Hansen
        var my = 0.0; var cAvg = 0.0; var phiHn = 0.0; var long = false
        rb.section("D. TAHANAN LATERAL TIANG – MOMEN MAKSIMUM (BRINCH HANSEN)") {
            val fb = value("Kuat lentur beton tiang", "fb = 0.40 · fc' · 10³", 0.4 * i.fc * 1000, "kN/m²", 0)
            val w = value("Tahanan momen", when (i.type) {
                PileType.PERSEGI -> "W = D³/6"; PileType.SPUN -> "W = π·(D⁴ − Di⁴)/(32·D)"; else -> "W = π·D³/32"
            }, g.sectionModulus, "m³", 5)
            my = value("Momen maksimum (leleh) tiang", "My = fb · W", fb * w, "kNm")
            cAvg = value("Kohesi tanah rata-rata sepanjang tiang", "č = Σ(cu·Li) / ΣLi", if (sumLi > 0) sumCuLi / sumLi else 0.0, "kN/m²")
            val d = i.diameter; val L = i.length
            value("Tinggi gaya lateral di atas tanah", "e", i.eLateral, "m")
            if (cAvg <= 0.0) {
                note("Kohesi rata-rata = 0 (tanah pasir): metode Brinch Hansen untuk lempung tidak berlaku. Tahanan lateral tidak dihitung.")
                return@section
            }
            note("pers.(1) f = Hn / (9·č·D)      pers.(2) g = L − (f + 1.5·D)")
            note("pers.(3) My = Hn·(e + 1.5·D + 0.5·f)      pers.(4) My = 9/4·D·č·g²")
            val a1 = value("Dari pers.(1)", "a1 = 1/(9·č·D)", 1 / (9 * cAvg * d), "1/kN", 6)
            val G = value("Dari pers.(2)", "G = L − 1.5·D", L - 1.5 * d, "m")
            val K = value("", "K = 9/4 · D · č", 9.0 / 4 * d * cAvg, "kN/m")
            val e15 = value("", "e + 1.5·D", i.eLateral + 1.5 * d, "m")
            note("Pers. kuadrat (3) = (4) :  0 = A·Hn² + B·Hn + C")
            val A = value("", "A = 0.5·a1 − K·a1²", 0.5 * a1 - K * a1 * a1, "", 8)
            val B = value("", "B = (e + 1.5·D) + 2·K·G·a1", e15 + 2 * K * G * a1, "", 4)
            val C = value("", "C = −K·G²", -K * G * G, "", 3)
            val hnShort = value("Tahanan lateral nominal (tiang pendek)", "Hn = [−B + √(B² − 4AC)] / 2A", (-B + sqrt(B * B - 4 * A * C)) / (2 * A), "kN")
            val f = value("", "f = a1 · Hn", a1 * hnShort, "m")
            val mmax = value("", "Mmax = Hn · (e + 1.5·D + 0.5·f)", hnShort * (e15 + 0.5 * f), "kNm")
            long = mmax > my
            text("Kategori tiang", "Mmax > My ?", if (long) "Tiang panjang" else "Tiang pendek")
            val hn = if (long) {
                note("Tiang panjang → dari pers.(3) dengan My :  0 = 0.5·a1·Hn² + (e + 1.5·D)·Hn − My")
                value("Tahanan lateral nominal (tiang panjang)", "Hn", (-e15 + sqrt(e15 * e15 + 4 * 0.5 * a1 * my)) / (2 * 0.5 * a1), "kN")
            } else hnShort
            value("Tahanan lateral nominal dipakai", "Hn", hn, "kN")
            value("Faktor reduksi kekuatan", "φ", i.phiLateral)
            phiHn = value("Tahanan lateral tiang", "φ · Hn", i.phiLateral * hn, "kN")
        }

        return PileResult(g, nAvg, nb, qp, qs, phiPnMat, phiPnSpt, phiPn, my, cAvg, phiHn, long, rb.build())
    }
}
