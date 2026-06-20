package online.blizzen.dailydraw

import online.blizzen.dailydraw.odds.DeVig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeVigTest {
    @Test fun americanToProbPositive() =
        assertEquals(0.4651, DeVig.americanToProb(115), 1e-3)

    @Test fun americanToProbNegative() =
        assertEquals(0.6000, DeVig.americanToProb(-150), 1e-3)

    @Test fun twoWayMatchesSpike() =
        // Mets 4+ runs: BetMGM O+115 / U-150 -> 43.7% (live spike value)
        assertEquals(0.437, DeVig.twoWay(115, -150), 1e-3)

    @Test fun oneSidedAppliesHaircut() {
        val raw = DeVig.americanToProb(285)            // 0.2597
        val est = DeVig.oneSided(285)                  // raw * (1 - 0.03)
        assertTrue(est < raw, "haircut must reduce the raw implied prob")
        assertEquals(raw * 0.97, est, 1e-6)
    }

    @Test fun probsStayInRange() {
        assertTrue(DeVig.oneSided(550) in 0.0..1.0)
        assertTrue(DeVig.twoWay(-100000, 100000) in 0.0..1.0)
    }
}
