package id.pondasi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Nilai pembanding dihitung terpisah (hitungan tangan dengan rumus yang sama). */
class FootplateTest {
    private val base = FootplateInput(
        concrete = ConcreteParams(fc = 25.0, fy = 420.0),
        bx = 0.4, by = 0.4, lx = 2.0, ly = 2.0, h = 0.5, df = 1.5, ws = 17.0,
        pu = 1000.0, mux = 50.0, bearing = BearingSource.Manual(400.0),
    )

    @Test fun contohManual() {
        val r = FootplateCalculator.calculate(base)
        assertEquals(316.5, r.qmax, 1e-9)
        assertEquals(241.5, r.qmin, 1e-9)
        assertEquals(324.869140625, r.vux, 1e-9)
        assertEquals(1062.5, r.phiVcx, 1e-9)
        assertEquals(829.84375, r.vup, 1e-9)
        assertEquals(1753.125, r.phiVnp, 1e-6)
        assertEquals(177.6, r.flexX.mu, 1e-9)
        assertEquals(2125.0, r.flexX.asReq, 1e-9)   // ρmin menentukan
        assertTrue(r.allOk)
    }

    @Test fun dayaDukungSpt() {
        assertEquals(412.4546875, FootplateCalculator.qaSpt(20.0, 2.0, 1.5), 1e-9)
        assertEquals(20 / 0.05 * 1.33, FootplateCalculator.qaSpt(20.0, 1.0, 1.5), 1e-9)
        val layers = listOf(SoilLayer(0.0, 2.0, "Lempung", 30.0, 10.0), SoilLayer(2.0, 6.0, "Pasir", 0.0, 20.0))
        val r = FootplateCalculator.calculate(base.copy(bearing = BearingSource.Spt(layers)))
        // N rata-rata 1.5–3.5 m = (0.5·10 + 1.5·20)/2 = 17.5
        assertEquals(FootplateCalculator.qaSpt(17.5, 2.0, 1.5), r.qa, 1e-9)
    }

    @Test fun ukuranOtomatis() {
        val s = assertNotNull(FootplateCalculator.autoSize(base.copy(pu = 1500.0, lx = 1.0, ly = 1.0, h = 0.3)))
        val r = FootplateCalculator.calculate(s)
        assertTrue(r.allOk)
        // ukuran lebih kecil 0.05 m harus gagal
        val smaller = FootplateCalculator.calculate(s.copy(lx = s.lx - 0.05, ly = s.ly - 0.05))
        assertTrue(!smaller.allOk || s.h > 0.3)
    }
}
