package id.pondasi.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/** Sumber daya dukung tanah izin. */
sealed interface BearingSource {
    /** Dari data SPT (Meyerhof, revisi Bowles, penurunan 25 mm). N diambil rata-rata Df … Df + B. */
    data class Spt(val layers: List<SoilLayer>, val nOverride: Double? = null) : BearingSource
    /** Daya dukung izin dimasukkan langsung, kN/m2. */
    data class Manual(val qa: Double) : BearingSource
}

data class FootplateInput(
    val concrete: ConcreteParams = ConcreteParams(),
    val bx: Double, val by: Double,     // kolom, m
    val lx: Double, val ly: Double,     // pondasi, m
    val h: Double,                      // tebal pondasi, m
    val df: Double,                     // kedalaman dasar pondasi, m
    val ws: Double = 17.0,              // berat volume tanah, kN/m3
    val alphaS: Double = 40.0,
    val pu: Double, val mux: Double = 0.0, val muy: Double = 0.0,
    val bearing: BearingSource,
    val dPrime: Double = 0.075,         // m
    val sFlexX: Double? = null, val sFlexY: Double? = null,
)

data class FootplateResult(
    val qa: Double, val qmax: Double, val qmin: Double,
    val vux: Double, val phiVcx: Double, val vuy: Double, val phiVcy: Double,
    val vup: Double, val phiVnp: Double,
    val flexX: FlexureResult, val flexY: FlexureResult,
    val allOk: Boolean,
    val report: Report,
)

object FootplateCalculator {

    /** qa (kN/m2) dari N-SPT, Meyerhof (1956) revisi Bowles. */
    fun qaSpt(n: Double, b: Double, df: Double): Double {
        val kd = min(1 + 0.33 * df / b, 1.33)
        return if (b <= 1.2) n / 0.05 * kd else n / 0.08 * ((b + 0.3) / b).let { it * it } * kd
    }

    fun calculate(i: FootplateInput): FootplateResult {
        val p = i.concrete
        val rb = ReportBuilder("PERHITUNGAN PONDASI TELAPAK (FOOTPLATE)")
        val z = (i.df - i.h).coerceAtLeast(0.0)
        val ok = mutableListOf<Boolean>()

        rb.section("DATA") {
            value("Kuat tekan beton", "fc'", p.fc, "MPa", 1)
            value("Kuat leleh baja tulangan", "fy", p.fy, "MPa", 0)
            value("Berat beton bertulang", "wc", p.wc, "kN/m³", 1)
            value("Lebar kolom arah x", "bx", i.bx, "m")
            value("Lebar kolom arah y", "by", i.by, "m")
            value("Lebar pondasi arah x", "Lx", i.lx, "m")
            value("Lebar pondasi arah y", "Ly", i.ly, "m")
            value("Tebal pondasi", "h", i.h, "m")
            value("Kedalaman dasar pondasi", "Df", i.df, "m")
            value("Tebal tanah di atas pondasi", "z = Df − h", z, "m")
            value("Berat volume tanah", "ws", i.ws, "kN/m³", 1)
            value("Posisi kolom (dalam = 40, tepi = 30, sudut = 20)", "αs", i.alphaS, "", 0)
            value("Gaya aksial terfaktor", "Pu", i.pu, "kN")
            value("Momen arah x terfaktor", "Mux", i.mux, "kNm")
            value("Momen arah y terfaktor", "Muy", i.muy, "kNm")
        }

        var qa = 0.0
        rb.section("1. DAYA DUKUNG TANAH IZIN") {
            when (val b = i.bearing) {
                is BearingSource.Manual -> qa = value("Daya dukung tanah izin (input)", "qa", b.qa, "kN/m²")
                is BearingSource.Spt -> {
                    val B = min(i.lx, i.ly)
                    note("Menurut Meyerhof (1956), revisi Bowles, untuk penurunan izin 25 mm :")
                    note("B ≤ 1.2 m : qa = N/0.05 · Kd      B > 1.2 m : qa = N/0.08 · [(B + 0.3)/B]² · Kd")
                    val nAuto = PileCalculator.averageN(b.layers, i.df, i.df + B)
                    val n = value("N-SPT rata-rata Df s.d. Df + B", if (b.nOverride != null) "N (input)" else "N = Σ(Li·N)/ΣLi", b.nOverride ?: nAuto)
                    value("Lebar pondasi terkecil", "B", B, "m")
                    value("Faktor kedalaman", "Kd = 1 + 0.33·Df/B ≤ 1.33", min(1 + 0.33 * i.df / B, 1.33), "", 3)
                    qa = value("Daya dukung tanah izin", "qa", qaSpt(n, B, i.df), "kN/m²")
                }
            }
        }

        var qmax = 0.0; var qmin = 0.0; var q = 0.0
        rb.section("2. KONTROL TEGANGAN TANAH") {
            val a = value("Luas dasar pondasi", "A = Lx · Ly", i.lx * i.ly, "m²", 3)
            val wx = value("Tahanan momen arah x", "Wx = 1/6 · Ly · Lx²", i.ly * i.lx * i.lx / 6, "m³", 3)
            val wy = value("Tahanan momen arah y", "Wy = 1/6 · Lx · Ly²", i.lx * i.ly * i.ly / 6, "m³", 3)
            q = value("Tekanan akibat berat pondasi dan tanah", "q = h·wc + z·ws", i.h * p.wc + z * i.ws, "kN/m²")
            val ex = value("Eksentrisitas arah x", "ex = Mux / Pu", if (i.pu > 0) i.mux / i.pu else 0.0, "m", 3)
            val ey = value("Eksentrisitas arah y", "ey = Muy / Pu", if (i.pu > 0) i.muy / i.pu else 0.0, "m", 3)
            ok += check("Kontrol eksentrisitas arah x (ex ≤ Lx/6)", abs(ex), "≤", i.lx / 6, abs(ex) <= i.lx / 6, "m", digits = 3)
            ok += check("Kontrol eksentrisitas arah y (ey ≤ Ly/6)", abs(ey), "≤", i.ly / 6, abs(ey) <= i.ly / 6, "m", digits = 3)
            qmax = value("Tegangan tanah maksimum", "qmax = Pu/A + Mux/Wx + Muy/Wy + q", i.pu / a + abs(i.mux) / wx + abs(i.muy) / wy + q, "kN/m²")
            qmin = value("Tegangan tanah minimum", "qmin = Pu/A − Mux/Wx − Muy/Wy + q", i.pu / a - abs(i.mux) / wx - abs(i.muy) / wy + q, "kN/m²")
            ok += check("Kontrol qmax ≤ qa", qmax, "≤", qa, qmax <= qa, "kN/m²")
            ok += check("Kontrol qmin ≥ 0", qmin, "≥", 0.0, qmin >= 0, "kN/m²", okText = "AMAN (OK) – tidak ada tegangan tarik", ngText = "TERJADI TEGANGAN TARIK")
        }

        val d = i.h - i.dPrime
        val betaC = maxOf(i.bx, i.by) / minOf(i.bx, i.by)

        fun oneWay(title: String, isX: Boolean): Pair<Double, Double> {
            var vu = 0.0; var phiVc = 0.0
            rb.section(title) {
                val L = if (isX) i.lx else i.ly
                val Lo = if (isX) i.ly else i.lx
                val col = if (isX) i.bx else i.by
                val s = if (isX) "x" else "y"
                value("Jarak pusat tulangan thd. sisi luar beton", "d'", i.dPrime, "m", 3)
                value("Tebal efektif pondasi", "d = h − d'", d, "m", 3)
                val ax = value("Jarak bidang kritis thd. sisi luar", "a$s = (L$s − b$s − d)/2", (L - col - d) / 2, "m", 4)
                val qx = value("Tegangan tanah pada bidang kritis", "q$s = qmin + (L$s − a$s)/L$s · (qmax − qmin)", qmin + (L - ax) / L * (qmax - qmin), "kN/m²")
                vu = value("Gaya geser arah $s", "Vu$s = [(q$s + qmax)/2 − q] · a$s · L${if (isX) "y" else "x"}", ((qx + qmax) / 2 - q) * ax * Lo, "kN")
                val b = value("Lebar bidang geser", "b = L${if (isX) "y" else "x"}", Lo * 1000, "mm", 0)
                val dmm = value("Tebal efektif", "d", d * 1000, "mm", 0)
                value("Rasio sisi panjang thd. sisi pendek kolom", "βc", betaC, "", 3)
                val vc = Concrete.shearVc(this, p.fc, betaC, i.alphaS, b, dmm)
                value("Faktor reduksi kekuatan geser", "φ", p.phiShear)
                phiVc = value("Kuat geser pondasi", "φ · Vc", p.phiShear * vc, "kN")
                ok += check("Kontrol geser arah $s (Vu ≤ φ·Vc)", vu, "≤", phiVc, vu <= phiVc, "kN")
            }
            return vu to phiVc
        }
        val (vux, phiVcx) = oneWay("3. TINJAUAN GESER ARAH X", true)
        val (vuy, phiVcy) = oneWay("4. TINJAUAN GESER ARAH Y", false)

        var vup = 0.0; var phiVnp = 0.0
        rb.section("5. TINJAUAN GESER DUA ARAH (PONS)") {
            value("Tebal efektif pondasi", "d", d, "m", 3)
            val cx = value("Lebar bidang geser pons arah x", "cx = bx + d", i.bx + d, "m", 3)
            val cy = value("Lebar bidang geser pons arah y", "cy = by + d", i.by + d, "m", 3)
            vup = value("Gaya geser pons", "Vup = (Lx·Ly − cx·cy) · [(qmax + qmin)/2 − q]", (i.lx * i.ly - cx * cy) * ((qmax + qmin) / 2 - q), "kN")
            val ap = value("Luas bidang geser pons", "Ap = 2 · (cx + cy) · d", 2 * (cx + cy) * d, "m²", 4)
            val bp = value("Lebar bidang geser pons", "bp = 2 · (cx + cy)", 2 * (cx + cy), "m", 3)
            value("Rasio sisi panjang thd. sisi pendek kolom", "βc", betaC, "", 3)
            val fp = Concrete.punchingFp(this, p.fc, betaC, i.alphaS, d, bp)
            value("Faktor reduksi kekuatan geser pons", "φ", p.phiShear)
            phiVnp = value("Kuat geser pons", "φ · Vnp = φ · Ap · fp · 10³", p.phiShear * ap * fp * 1000, "kN")
            ok += check("Kontrol geser pons (Vup ≤ φ·Vnp)", vup, "≤", phiVnp, vup <= phiVnp, "kN")
        }

        fun flex(title: String, isX: Boolean, sIn: Double?): FlexureResult {
            lateinit var r: FlexureResult
            rb.section(title) {
                val L = if (isX) i.lx else i.ly
                val Lo = if (isX) i.ly else i.lx
                val col = if (isX) i.bx else i.by
                val s = if (isX) "x" else "y"
                val ax = value("Jarak tepi kolom thd. sisi luar pondasi", "a$s = (L$s − b$s)/2", (L - col) / 2, "m", 3)
                val qx = value("Tegangan tanah pada muka kolom", "q$s = qmin + (L$s − a$s)/L$s · (qmax − qmin)", qmin + (L - ax) / L * (qmax - qmin), "kN/m²")
                val mu = value("Momen yang terjadi pada pondasi", "Mu$s = ½ · a$s² · [q$s − q + ⅔ · (qmax − q$s)] · L${if (isX) "y" else "x"}",
                    0.5 * ax * ax * (qx - q + 2.0 / 3 * (qmax - qx)) * Lo, "kNm")
                r = Concrete.flexure(this, p, mu, Lo * 1000, i.h * 1000, i.dPrime * 1000, sIn)
                ok += r.ok
            }
            return r
        }
        val fx = flex("6.1 TULANGAN LENTUR ARAH X", true, i.sFlexX)
        val fy = flex("6.2 TULANGAN LENTUR ARAH Y", false, i.sFlexY)

        return FootplateResult(qa, qmax, qmin, vux, phiVcx, vuy, phiVcy, vup, phiVnp, fx, fy, ok.all { it }, rb.build())
    }

    /**
     * Cari ukuran pondasi bujur sangkar terkecil (kelipatan 0.05 m) dan tebal terkecil (kelipatan 0.05 m)
     * yang memenuhi semua kontrol. Mengembalikan null bila tidak ditemukan sampai 8 m × 1.5 m.
     */
    fun autoSize(base: FootplateInput): FootplateInput? {
        var b = round(maxOf(base.bx, base.by, 0.6) * 20) / 20
        while (b <= 8.0 + 1e-9) {
            var h = 0.3
            while (h <= 1.5 + 1e-9 && h <= base.df) {
                val cand = base.copy(lx = b, ly = b, h = h)
                val r = calculate(cand)
                if (r.allOk) return cand
                // jika tegangan tanah gagal, menambah tebal tidak membantu → perbesar B
                if (r.qmax > r.qa || r.qmin < 0) break
                h = round((h + 0.05) * 100) / 100
            }
            b = round((b + 0.05) * 100) / 100
        }
        return null
    }
}
