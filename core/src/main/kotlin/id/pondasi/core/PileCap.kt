package id.pondasi.core

import kotlin.math.abs
import kotlin.math.sqrt

data class PilePos(val x: Double, val y: Double)

data class PileCapInput(
    val concrete: ConcreteParams = ConcreteParams(),
    val bx: Double, val by: Double,     // ukuran kolom, m
    val h: Double,                       // tebal pilecap, m
    val lx: Double, val ly: Double,      // ukuran pilecap, m
    val soilAbove: Double = 0.0,         // tebal tanah di atas pilecap, m
    val ws: Double = 16.0,               // kN/m3
    val alphaS: Double = 40.0,           // 40 dalam, 30 tepi, 20 sudut
    val pu: Double,                      // gaya aksial kolom terfaktor, kN
    val mux: Double = 0.0, val muy: Double = 0.0,
    val hux: Double = 0.0, val huy: Double = 0.0,
    val mt: Double = 0.0,                // momen puntir, kNm
    val piles: List<PilePos>,
    val phiPn: Double,                   // tahanan aksial 1 tiang, kN
    val phiHn: Double,                   // tahanan lateral 1 tiang, kN
    val dPrime: Double = 0.175,          // m, jarak pusat tulangan ke sisi luar
    val sFlexX: Double? = null, val sFlexY: Double? = null, val sShrink: Double? = null,
) {
    companion object {
        /** Susunan grid nx × ny dengan jarak sx, sy; opsi kosongkan tiang tengah. */
        fun grid(nx: Int, ny: Int, sx: Double, sy: Double, skipCenter: Boolean = false): List<PilePos> {
            val out = mutableListOf<PilePos>()
            for (i in 0 until nx) for (j in 0 until ny) {
                val x = (i - (nx - 1) / 2.0) * sx
                val y = (j - (ny - 1) / 2.0) * sy
                if (skipCenter && abs(x) < 1e-9 && abs(y) < 1e-9) continue
                out += PilePos(x, y)
            }
            return out
        }
    }
}

data class PileCapResult(
    val pumax: Double, val pumin: Double, val humax: Double,
    val vux: Double, val phiVcx: Double, val vuy: Double, val phiVcy: Double,
    val phiVnp: Double,
    val flexX: FlexureResult, val flexY: FlexureResult,
    val allOk: Boolean,
    val report: Report,
)

object PileCapCalculator {
    private const val EPS = 1e-9

    fun calculate(i: PileCapInput): PileCapResult {
        val p = i.concrete
        val rb = ReportBuilder("PERHITUNGAN KEKUATAN FONDASI (PILECAP)")
        val n = i.piles.size
        require(n > 0) { "Jumlah tiang harus > 0" }
        val sx2 = i.piles.sumOf { it.x * it.x }
        val sy2 = i.piles.sumOf { it.y * it.y }
        val xmax = i.piles.maxOf { abs(it.x) }
        val ymax = i.piles.maxOf { abs(it.y) }

        rb.section("DATA") {
            value("Kuat tekan beton", "fc'", p.fc, "MPa", 1)
            value("Kuat leleh baja tulangan", "fy", p.fy, "MPa", 0)
            value("Berat beton bertulang", "wc", p.wc, "kN/m³", 1)
            value("Lebar kolom arah x", "bx", i.bx, "m")
            value("Lebar kolom arah y", "by", i.by, "m")
            value("Tebal pilecap", "h", i.h, "m")
            value("Lebar pilecap arah x", "Lx", i.lx, "m")
            value("Lebar pilecap arah y", "Ly", i.ly, "m")
            value("Tebal tanah di atas pilecap", "z", i.soilAbove, "m")
            value("Berat volume tanah", "ws", i.ws, "kN/m³", 1)
            value("Posisi kolom (dalam = 40, tepi = 30, sudut = 20)", "αs", i.alphaS, "", 0)
            value("Gaya aksial kolom terfaktor", "Puk", i.pu, "kN")
            value("Momen arah x terfaktor", "Mux", i.mux, "kNm")
            value("Momen arah y terfaktor", "Muy", i.muy, "kNm")
            value("Gaya lateral arah x terfaktor", "Hux", i.hux, "kN")
            value("Gaya lateral arah y terfaktor", "Huy", i.huy, "kN")
            value("Momen puntir terfaktor", "Mt", i.mt, "kNm")
            value("Tahanan aksial satu tiang", "φ · Pn", i.phiPn, "kN")
            value("Tahanan lateral satu tiang", "φ · Hn", i.phiHn, "kN")
        }

        // 1. Gaya aksial tiang
        var pumax = 0.0; var pumin = 0.0
        val ok = mutableListOf<Boolean>()
        rb.section("1. GAYA AKSIAL PADA TIANG") {
            val ws = value("Berat tanah di atas pilecap", "Ws = Lx · Ly · z · ws", i.lx * i.ly * i.soilAbove * i.ws, "kN")
            val wc = value("Berat pilecap", "Wc = Lx · Ly · h · wc", i.lx * i.ly * i.h * p.wc, "kN")
            val pu = value("Total gaya aksial terfaktor", "Pu = Puk + 1.2·Ws + 1.2·Wc", i.pu + 1.2 * ws + 1.2 * wc, "kN")
            value("Jumlah tiang", "n", n.toDouble(), "bh", 0)
            value("", "Σx²", sx2, "m²", 3)
            value("", "Σy²", sy2, "m²", 3)
            note("Gaya pada tiang ke-i : pi = Pu/n + Mux·xi/Σx² + Muy·yi/Σy²")
            val forces = i.piles.map { pu / n + (if (sx2 > 0) i.mux * it.x / sx2 else 0.0) + (if (sy2 > 0) i.muy * it.y / sy2 else 0.0) }
            table(listOf("No", "x (m)", "y (m)", "pi (kN)"),
                i.piles.mapIndexed { k, pp -> listOf("${k + 1}", fmt(pp.x), fmt(pp.y), fmt(forces[k])) })
            pumax = value("Gaya aksial maksimum", "pumax = Pu/n + Mux·xmax/Σx² + Muy·ymax/Σy²", forces.max(), "kN")
            pumin = value("Gaya aksial minimum", "pumin = Pu/n − Mux·xmax/Σx² − Muy·ymax/Σy²", forces.min(), "kN")
            ok += check("Kontrol gaya aksial tiang (pumax ≤ φ·Pn)", pumax, "≤", i.phiPn, pumax <= i.phiPn, "kN")
            ok += check("Kontrol tiang tidak tertarik (pumin ≥ 0)", pumin, "≥", 0.0, pumin >= 0, "kN",
                okText = "AMAN (OK) – tidak ada tarik", ngText = "TERJADI GAYA TARIK")
        }

        // 2. Lateral
        var humax = 0.0
        rb.section("2. GAYA LATERAL PADA TIANG") {
            val hx = value("Gaya lateral pada tiang akibat geser arah x", "hux = Hux / n", i.hux / n, "kN")
            val hy = value("Gaya lateral pada tiang akibat geser arah y", "huy = Huy / n", i.huy / n, "kN")
            val hu = value("Resultan gaya lateral akibat geser", "hu = √(hux² + huy²)", sqrt(hx * hx + hy * hy), "kN")
            val r = value("Jarak tiang terjauh ke pusat", "r = √(xmax² + ymax²)", sqrt(xmax * xmax + ymax * ymax), "m", 3)
            val ht = value("Gaya lateral pada tiang akibat momen torsi", "H torsi = Mt / (n · r)", if (r > 0) i.mt / (n * r) else 0.0, "kN")
            humax = value("Gaya lateral maksimum pada tiang", "humax = hu + H torsi", hu + ht, "kN")
            ok += check("Kontrol gaya lateral tiang (humax ≤ φ·Hn)", humax, "≤", i.phiHn, humax <= i.phiHn, "kN",
                ngText = "BAHAYA (NG)")
        }

        val d = i.h - i.dPrime
        val betaC = maxOf(i.bx, i.by) / minOf(i.bx, i.by)

        fun oneWay(title: String, isX: Boolean): Pair<Double, Double> {
            var vu = 0.0; var phiVc = 0.0
            rb.section(title) {
                val L = if (isX) i.lx else i.ly
                val Lo = if (isX) i.ly else i.lx
                val col = if (isX) i.bx else i.by
                val ax = if (isX) "x" else "y"
                value("Jarak pusat tulangan thd. sisi luar beton", "d'", i.dPrime, "m", 3)
                value("Tebal efektif pilecap", "d = h − d'", d, "m", 3)
                val c = value("Jarak bidang kritis thd. sisi luar", "c = (L$ax − b$ax − d)/2", (L - col - d) / 2, "m", 4)
                val w1 = value("Berat beton", "W1 = c · L${if (isX) "y" else "x"} · h · wc", c * Lo * i.h * p.wc, "kN")
                val w2 = value("Berat tanah", "W2 = c · L${if (isX) "y" else "x"} · z · ws", c * Lo * i.soilAbove * i.ws, "kN")
                val xc = col / 2 + d / 2
                val nOut = i.piles.count { (if (isX) it.x else it.y) > xc + EPS }
                value("Jumlah tiang di luar bidang kritis", "n,luar", nOut.toDouble(), "bh", 0)
                vu = value("Gaya geser arah $ax", "Vu = n,luar · pumax − W1 − W2", nOut * pumax - w1 - w2, "kN")
                val b = value("Lebar bidang geser", "b = L${if (isX) "y" else "x"}", Lo * 1000, "mm", 0)
                val dmm = value("Tebal efektif", "d", d * 1000, "mm", 0)
                value("Rasio sisi panjang thd. sisi pendek kolom", "βc", betaC, "", 3)
                val vc = Concrete.shearVc(this, p.fc, betaC, i.alphaS, b, dmm)
                value("Faktor reduksi kekuatan geser", "φ", p.phiShear)
                phiVc = value("Kuat geser pilecap", "φ · Vc", p.phiShear * vc, "kN")
                ok += check("Kontrol geser arah $ax (Vu ≤ φ·Vc)", vu, "≤", phiVc, vu <= phiVc, "kN")
            }
            return vu to phiVc
        }
        val (vux, phiVcx) = oneWay("3. TINJAUAN GESER ARAH X", true)
        val (vuy, phiVcy) = oneWay("4. TINJAUAN GESER ARAH Y", false)

        var phiVnp = 0.0
        rb.section("5. TINJAUAN GESER DUA ARAH (PONS)") {
            value("Tebal efektif pilecap", "d = h − d'", d, "m", 3)
            val bxp = value("Lebar bidang geser pons arah x", "Bx = bx + d", i.bx + d, "m", 3)
            val byp = value("Lebar bidang geser pons arah y", "By = by + d", i.by + d, "m", 3)
            value("Gaya geser pons akibat beban terfaktor kolom", "Puk", i.pu, "kN")
            val ap = value("Luas bidang geser pons", "Ap = 2 · (Bx + By) · d", 2 * (bxp + byp) * d, "m²", 4)
            val bp = value("Lebar bidang geser pons", "bp = 2 · (Bx + By)", 2 * (bxp + byp), "m", 3)
            value("Rasio sisi panjang thd. sisi pendek kolom", "βc", betaC, "", 3)
            val fp = Concrete.punchingFp(this, p.fc, betaC, i.alphaS, d, bp)
            value("Faktor reduksi kekuatan geser pons", "φ", p.phiShear)
            phiVnp = value("Kuat geser pons", "φ · Vnp = φ · Ap · fp · 10³", p.phiShear * ap * fp * 1000, "kN")
            ok += check("Kontrol geser pons (Puk ≤ φ·Vnp)", i.pu, "≤", phiVnp, i.pu <= phiVnp, "kN")
        }

        fun flex(title: String, isX: Boolean, sInput: Double?): FlexureResult {
            lateinit var r: FlexureResult
            rb.section(title) {
                val L = if (isX) i.lx else i.ly
                val Lo = if (isX) i.ly else i.lx
                val col = if (isX) i.bx else i.by
                val ax = if (isX) "x" else "y"
                val c = value("Jarak tepi kolom thd. sisi luar pilecap", "c = (L$ax − b$ax)/2", (L - col) / 2, "m", 3)
                val outside = i.piles.filter { (if (isX) it.x else it.y) > col / 2 + EPS }
                val arm = outside.sumOf { (if (isX) it.x else it.y) - col / 2 }
                value("Jumlah tiang di luar muka kolom", "n,luar", outside.size.toDouble(), "bh", 0)
                value("Jumlah lengan tiang thd. muka kolom", "Σe = Σ(xi − b$ax/2)", arm, "m", 3)
                val w1 = value("Berat beton", "W1 = c · L${if (isX) "y" else "x"} · h · wc", c * Lo * i.h * p.wc, "kN")
                val w2 = value("Berat tanah", "W2 = c · L${if (isX) "y" else "x"} · z · ws", c * Lo * i.soilAbove * i.ws, "kN")
                val mu = value("Momen yang terjadi pada pilecap", "Mu = pumax·Σe − W1·c/2 − W2·c/2", pumax * arm - (w1 + w2) * c / 2, "kNm")
                r = Concrete.flexure(this, p, mu, Lo * 1000, i.h * 1000, i.dPrime * 1000, sInput)
                ok += r.ok
            }
            return r
        }
        val fx = flex("6.1 TULANGAN LENTUR ARAH X", true, i.sFlexX)
        val fy = flex("6.2 TULANGAN LENTUR ARAH Y", false, i.sFlexY)

        rb.section("6.3 TULANGAN SUSUT") {
            ok += Concrete.shrinkage(this, p, minOf(i.lx, i.ly) * 1000, (i.h - i.dPrime) * 1000, i.sShrink).second
        }

        return PileCapResult(pumax, pumin, humax, vux, phiVcx, vuy, phiVcy, phiVnp, fx, fy, ok.all { it }, rb.build())
    }
}
