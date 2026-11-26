package id.pondasi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dicocokkan dengan contoh "Hitungan_Pondasi_TC_Cipete.xlsx" (pondasi tower crane Potain MC 205B,
 * spun pile D60, L = 10 m, pilecap 6 × 6 × 1.7 m, 8 tiang).
 */
class CipeteExcelTest {

    private fun layer(z1: Double, z2: Double, d: String, cu: Double, n: Double) = SoilLayer(z1, z2, d, cu, n)

    private val layers = listOf(
        layer(0.0, 2.0, "Lempung", 30.0, 6.0),
        layer(2.0, 4.0, "Lempung", 55.0, 12.0),
        layer(4.0, 6.0, "Lempung", 50.0, 8.0),
        layer(6.0, 8.0, "Lempung", 55.0, 13.0),
        layer(8.0, 10.0, "Lempung Pasir", 200.0, 60.0),
        layer(10.0, 12.0, "Pasir kasar gravel", 0.0, 50.0),
        layer(12.0, 14.0, "Pasir kasar gravel", 0.0, 60.0),
        layer(14.0, 16.0, "Pasir kasar gravel", 0.0, 60.0),
        layer(16.0, 18.0, "Pasir kasar gravel", 0.0, 56.0),
        layer(18.0, 20.0, "Pasir kasar gravel", 0.0, 40.0),
    )

    private val pileInput = PileInput(
        type = PileType.BULAT, diameter = 0.6, length = 10.0, fc = 52.0, wc = 24.0,
        layers = layers, nbOverride = 60.0, eLateral = 0.2,
    )

    @Test fun dayaDukungTiang() {
        val r = PileCalculator.calculate(pileInput)
        assertEquals(19.8, r.nAvg, 1e-9)
        assertEquals(2597.61960243541, r.phiPnMaterial, 1e-6)
        assertEquals(678.584013175395, r.qp, 1e-6)
        assertEquals(373.221207246467, r.qs, 1e-6)
        assertEquals(631.083132253117, r.phiPnSpt, 1e-6)
        assertEquals(630.0, r.phiPn, 1e-9)
        assertEquals(441.079608564007, r.my, 1e-6)
        assertEquals(78.0, r.cAvg, 1e-9)
        assertTrue(r.longPile)
        assertEquals(181.402295214421, r.phiHn, 1e-6)
    }

    private fun capInput(phiHn: Double) = PileCapInput(
        concrete = ConcreteParams(fc = 30.0, fy = 420.0, wc = 24.0, beta1 = 0.85, phiFlexure = 0.8,
            barDia = 25.0, shrinkBarDia = 16.0, sMax = 200.0),
        bx = 2.0, by = 2.0, h = 1.7, lx = 6.0, ly = 6.0, soilAbove = 0.0, ws = 16.0, alphaS = 40.0,
        pu = 760.95, mux = 931.62, muy = 931.62, hux = 114.98, huy = 114.98, mt = 3131.48,
        piles = PileCapInput.grid(3, 3, 2.4, 2.4, skipCenter = true),
        phiPn = 630.0, phiHn = phiHn, dPrime = 0.175, sShrink = 80.0,
    )

    @Test fun pilecap() {
        val r = PileCapCalculator.calculate(capInput(181.402295214421))
        // Sama dengan Excel
        assertEquals(444.8304166666667, r.pumax, 1e-9)
        assertEquals(135.65342729760573, r.humax, 1e-9)
        assertEquals(12529.153502930672, r.phiVcy, 1e-6)   // φVc arah y (d' = 0.175)
        assertEquals(29443.510731887025, r.phiVnp, 1e-6)
        assertEquals(22875.0, r.flexX.asReq, 1e-6)          // ρmin menentukan
        assertEquals(120.0, r.flexX.sUsed, 1e-9)            // D25-120
        assertEquals(24543.692606170258, r.flexX.asUsed, 1e-6)

        // Berbeda dari Excel (koreksi): pumin dari tiang sudut, dan geser/momen hanya memakai
        // 3 tiang pada satu sisi bidang kritis (Excel menjumlahkan 6 tiang dari kedua sisi).
        assertEquals(2523.51 / 8 - 2 * 931.62 * 2.4 / 34.56, r.pumin, 1e-9)
        assertEquals(3 * 444.8304166666667 - 302.94, r.vuy, 1e-6)
        assertEquals(3 * 444.8304166666667 * 1.4 - 489.6 * 2.0 / 2, r.flexX.mu, 1e-6)
        assertTrue(r.allOk)
        assertTrue(r.report.toPlainText().contains("D25 - 120"))
    }
}
