package online.blizzen.dailydraw

import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.ProbMethod
import online.blizzen.dailydraw.model.StatKey
import online.blizzen.dailydraw.odds.Bookmaker
import online.blizzen.dailydraw.odds.EventOdds
import online.blizzen.dailydraw.odds.Market
import online.blizzen.dailydraw.odds.Outcome
import online.blizzen.dailydraw.odds.ProbabilityEstimator
import online.blizzen.dailydraw.rank.Ranker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixture mirroring the live 2026-06-20 NYM@PHI spike data. */
private val spikeOdds = EventOdds(
    id = "evt", awayTeam = "New York Mets", homeTeam = "Philadelphia Phillies",
    bookmakers = listOf(
        Bookmaker("betmgm", "BetMGM", listOf(
            Market("alternate_team_totals", listOf(
                Outcome("Over", 115, 3.5, "New York Mets"),
                Outcome("Under", -150, 3.5, "New York Mets"),
            )),
        )),
        Bookmaker("draftkings", "DraftKings", listOf(
            Market("batter_hits_runs_rbis_alternate", listOf(
                Outcome("Over", 285, 2.5, "Justin Crawford"),   // one-sided
            )),
        )),
        Bookmaker("betrivers", "BetRivers", listOf(
            Market("batter_home_runs", listOf(
                Outcome("Over", 550, 0.5, "Francisco Alvarez"),  // one-sided
            )),
        )),
    ),
)

private val hand = listOf(
    Card("Mets", StatKey.TEAM_RUNS, 4, 5.25),
    Card("Justin Crawford", StatKey.BATTER_HITS_RUNS_RBIS, 3, 6.75),
    Card("Phillies", StatKey.TEAM_RUN_FIRST_INNING, 1, 5.0),
    Card("Francisco Alvarez", StatKey.BATTER_HOME_RUNS, 1, 5.5),
)

class PipelineTest {
    private val est = ProbabilityEstimator()

    @Test fun twoWayTeamTotalFallsThroughToAlternate() {
        // standard team_totals absent -> estimator must try alternate_team_totals
        val e = est.estimate(hand[0], spikeOdds)
        assertEquals(ProbMethod.TWO_WAY_DEVIG, e.method)
        assertEquals(0.437, e.prob!!, 1e-3)
    }

    @Test fun oneSidedPlayerPropsUseHaircut() {
        val crawford = est.estimate(hand[1], spikeOdds)
        assertEquals(ProbMethod.ONE_SIDED_HAIRCUT, crawford.method)
        assertTrue(crawford.prob!! in 0.24..0.27)

        val alvarez = est.estimate(hand[3], spikeOdds)
        assertEquals(ProbMethod.ONE_SIDED_HAIRCUT, alvarez.method)
        assertTrue(alvarez.prob!! in 0.14..0.16)
    }

    @Test fun coverageGapResolvesToNoData() {
        val e = est.estimate(hand[2], spikeOdds)
        assertEquals(ProbMethod.NONE, e.method)
        assertNull(e.prob)
    }

    @Test fun rankerOrdersByEvAndSeparatesNoData() {
        val r = Ranker(est).rank(hand, spikeOdds)
        assertEquals(3, r.ranked.size)
        assertEquals(1, r.noData.size)
        // EV order: Mets (~2.29) > Crawford (~1.70) > Alvarez (~0.82)
        assertEquals("Mets", r.ranked[0].card.subjectName)
        assertEquals("Justin Crawford", r.ranked[1].card.subjectName)
        assertEquals("Francisco Alvarez", r.ranked[2].card.subjectName)
        assertTrue(r.ranked[0].ev!! > r.ranked[1].ev!!)
        assertEquals(1, r.manualFillNeeded) // only 3 priced, need 1 manual
    }
}
