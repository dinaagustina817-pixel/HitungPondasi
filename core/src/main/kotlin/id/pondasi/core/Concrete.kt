package id.pondasi.core

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/** Parameter bahan beton & tulangan untuk pelat pondasi (telapak / pilecap). */
data class ConcreteParams(
    val fc: Double = 30.0,              // MPa
    val fy: Double = 420.0,             // MPa
    val wc: Double = 24.0,              // kN/m3
    val beta1: Double = 0.85,
    val phiFlexure: Double = 0.80,      // SNI 2847:2019 memakai 0.90 untuk penampang terkendali tarik
    val phiShear: Double = 0.75,
    val rhoMin: Double = 0.0025,
    val rhoShrink: Double = 0.0014,
    val barDia: Double = 25.0,          // mm, tulangan lentur
    val shrinkBarDia: Double = 16.0,    // mm, tulangan susut
    val sMax: Double = 200.0,           // mm
)

/** Hasil desain tulangan lentur satu arah. */
data class FlexureResult(
    val mu: Double, val asReq: Double, val sReq: Double, val sUsed: Double, val asUsed: Double,
    val rnOk: Boolean, val ok: Boolean,
)

internal object Concrete {

    /** Kuat geser beton (diambil terkecil dari tiga rumus), kN. b, d dalam mm. */
    fun shearVc(sb: SectionBuilder, fc: Double, betaC: Double, alphaS: Double, b: Double, d: Double): Double {
        sb.note("Kuat geser beton, diambil nilai terkecil dari Vc berikut :")
        val v1 = sb.value("", "Vc = [1 + 2/βc] · √fc' · b · d / 6 · 10⁻³", (1 + 2 / betaC) * sqrt(fc) * b * d / 6 * 1e-3, "kN")
        val v2 = sb.value("", "Vc = [αs·d/b + 2] · √fc' · b · d / 12 · 10⁻³", (alphaS * d / b + 2) * sqrt(fc) * b * d / 12 * 1e-3, "kN")
        val v3 = sb.value("", "Vc = 1/3 · √fc' · b · d · 10⁻³", sqrt(fc) * b * d / 3 * 1e-3, "kN")
        return sb.value("Diambil, kuat geser beton", "Vc", minOf(v1, v2, v3), "kN")
    }

    /** Tegangan geser pons yang disyaratkan, MPa. d, bp dalam m. */
    fun punchingFp(sb: SectionBuilder, fc: Double, betaC: Double, alphaS: Double, d: Double, bp: Double): Double {
        sb.note("Tegangan geser pons, diambil nilai terkecil dari fp berikut :")
        val f1 = sb.value("", "fp = [1 + 2/βc] · √fc' / 6", (1 + 2 / betaC) * sqrt(fc) / 6, "MPa", 3)
        val f2 = sb.value("", "fp = [αs·d/bp + 2] · √fc' / 12", (alphaS * d / bp + 2) * sqrt(fc) / 12, "MPa", 3)
        val f3 = sb.value("", "fp = 1/3 · √fc'", sqrt(fc) / 3, "MPa", 3)
        return sb.value("Tegangan geser pons yang disyaratkan", "fp", minOf(f1, f2, f3), "MPa", 3)
    }

    /** Jarak tulangan otomatis: dibulatkan ke bawah kelipatan 10 mm dan ≤ smax. */
    fun autoSpacing(sReq: Double, sMax: Double): Double = min(floor(sReq / 10.0) * 10.0, sMax).coerceAtLeast(50.0)

    /**
     * Desain tulangan lentur pelat. Mu (kNm), b, h, dPrime dalam mm.
     * @param sUsedInput jarak tulangan pilihan pengguna (mm) atau null = otomatis.
     */
    fun flexure(
        sb: SectionBuilder, p: ConcreteParams, mu: Double, b: Double, h: Double, dPrime: Double, sUsedInput: Double?,
    ): FlexureResult {
        sb.value("Lebar pelat yang ditinjau", "b", b, "mm", 0)
        sb.value("Tebal pelat", "h", h, "mm", 0)
        sb.value("Jarak pusat tulangan thd. sisi luar beton", "d'", dPrime, "mm", 0)
        val d = sb.value("Tebal efektif pelat", "d = h − d'", h - dPrime, "mm", 0)
        sb.value("Kuat tekan beton", "fc'", p.fc, "MPa", 1)
        sb.value("Kuat leleh baja tulangan", "fy", p.fy, "MPa", 0)
        sb.value("Faktor distribusi tegangan beton", "β1", p.beta1, "", 3)
        val rhoB = sb.value("", "ρb = β1·0.85·fc'/fy · 600/(600+fy)", p.beta1 * 0.85 * p.fc / p.fy * 600 / (600 + p.fy), "", 5)
        sb.value("Faktor reduksi kekuatan lentur", "φ", p.phiFlexure, "", 2)
        val rMax = sb.value("", "Rmax = 0.75·ρb·fy·[1 − ½·0.75·ρb·fy/(0.85·fc')]",
            0.75 * rhoB * p.fy * (1 - 0.5 * 0.75 * rhoB * p.fy / (0.85 * p.fc)), "", 4)
        val mn = sb.value("", "Mn = Mu / φ", mu / p.phiFlexure, "kNm")
        val rn = sb.value("", "Rn = Mn · 10⁶ / (b · d²)", mn * 1e6 / (b * d * d), "", 5)
        val rnOk = sb.check("Kontrol Rn < Rmax", rn, "<", rMax, rn < rMax, okText = "AMAN (OK)", ngText = "PERBESAR TEBAL (NG)", digits = 4)
        val inner = 1 - 2 * rn / (0.85 * p.fc)
        val rhoCalc = if (inner >= 0) 0.85 * p.fc / p.fy * (1 - sqrt(inner)) else Double.NaN
        val rhoReq = sb.value("Rasio tulangan yang diperlukan", "ρ = 0.85·fc'/fy · [1 − √{1 − 2·Rn/(0.85·fc')}]", maxOf(0.0, rhoCalc), "", 6)
        sb.value("Rasio tulangan minimum", "ρmin", p.rhoMin, "", 4)
        val rho = sb.value("Rasio tulangan yang digunakan", "ρ", if (rhoReq.isNaN()) rhoReq else maxOf(rhoReq, p.rhoMin), "", 6)
        val asReq = sb.value("Luas tulangan yang diperlukan", "As = ρ · b · d", rho * b * d, "mm²")
        sb.value("Diameter tulangan yang digunakan", "D", p.barDia, "mm", 0)
        val sReq = sb.value("Jarak tulangan yang diperlukan", "s = π/4 · D² · b / As", PI / 4 * p.barDia * p.barDia * b / asReq, "mm")
        sb.value("Jarak tulangan maksimum", "smax", p.sMax, "mm", 0)
        val sUsed = sb.value("Jarak tulangan yang digunakan", "s", sUsedInput ?: autoSpacing(sReq, p.sMax), "mm", 0)
        sb.text("Digunakan tulangan", "", "D${fmt(p.barDia, 0)} - ${fmt(sUsed, 0)}")
        val asUsed = sb.value("Luas tulangan terpakai", "As = π/4 · D² · b / s", PI / 4 * p.barDia * p.barDia * b / sUsed, "mm²")
        val ok = sb.check("Kontrol tulangan lentur (As terpakai ≥ As perlu, s ≤ smax)", asUsed, "≥", asReq,
            rnOk && asUsed >= asReq && sUsed <= p.sMax, "mm²")
        return FlexureResult(mu, asReq, sReq, sUsed, asUsed, rnOk, ok)
    }

    /** Tulangan susut. b, d dalam mm. Mengembalikan jarak terpakai (mm). */
    fun shrinkage(sb: SectionBuilder, p: ConcreteParams, b: Double, d: Double, sUsedInput: Double?): Pair<Double, Boolean> {
        sb.value("Rasio tulangan susut minimum", "ρsmin", p.rhoShrink, "", 4)
        val asS = sb.value("Luas tulangan susut", "As = ρsmin · b · d", p.rhoShrink * b * d, "mm²")
        sb.value("Diameter tulangan yang digunakan", "Ø", p.shrinkBarDia, "mm", 0)
        val sReq = sb.value("Jarak tulangan susut yang diperlukan", "s = π/4 · Ø² · b / As", PI / 4 * p.shrinkBarDia * p.shrinkBarDia * b / asS, "mm")
        sb.value("Jarak tulangan susut maksimum", "smax", p.sMax, "mm", 0)
        val s = sb.value("Jarak tulangan susut yang digunakan", "s", sUsedInput ?: autoSpacing(sReq, p.sMax), "mm", 0)
        sb.text("Digunakan tulangan susut", "", "D${fmt(p.shrinkBarDia, 0)} - ${fmt(s, 0)}")
        val ok = sb.check("Kontrol tulangan susut (s ≤ s perlu, s ≤ smax)", s, "≤", min(sReq, p.sMax), s <= sReq && s <= p.sMax, "mm")
        return s to ok
    }
}
