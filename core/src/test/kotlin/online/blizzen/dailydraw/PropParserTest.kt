package online.blizzen.dailydraw

import online.blizzen.dailydraw.model.StatKey
import online.blizzen.dailydraw.parse.ParseOutcome
import online.blizzen.dailydraw.parse.PropParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PropParserTest {
    private val parser = PropParser()

    private fun parsed(text: String): ParseOutcome.Parsed {
        val r = parser.parse(text)
        assertIs<ParseOutcome.Parsed>(r, "expected Parsed, got $r")
        return r
    }

    // --- the four real MLB cards (OCR-shaped text from the screenshots) ---

    @Test fun teamRuns() {
        val r = parsed("""
            NYM VS PHI
            5.25 Points
            3.5 POINTS • 1.5X MULTIPLIER
            Mets
            34 - 41
            WILL SCORE 4+ RUNS
        """.trimIndent())
        assertEquals("Mets", r.card.subjectName)
        assertEquals(StatKey.TEAM_RUNS, r.card.stat)
        assertEquals(4, r.card.threshold)
        assertEquals(5.25, r.card.displayedPoints, 1e-9)
        assertEquals(3.5, r.card.overPoint, 1e-9)  // "4+" -> Over 3.5
        assertTrue(r.warnings.isEmpty(), "unexpected warnings: ${r.warnings}")
    }

    @Test fun playerHitsRunsRbisWrapped() {
        val r = parsed("""
            6.75 Points
            4.5 POINTS • 1.5X MULTIPLIER
            Justin Crawford
            CF • #2
            WILL RECORD 3+ COMBINED
            HITS + RUNS + RBIS
        """.trimIndent())
        assertEquals("Justin Crawford", r.card.subjectName)
        assertEquals(StatKey.BATTER_HITS_RUNS_RBIS, r.card.stat)
        assertEquals(3, r.card.threshold)
        assertEquals(6.75, r.card.displayedPoints, 1e-9)
    }

    @Test fun teamFirstInningIsCoverageGapStat() {
        val r = parsed("""
            5 Points
            4 POINTS • 1.25X MULTIPLIER
            Phillies
            40 - 35
            WILL SCORE A RUN IN THE 1ST
            INNING
        """.trimIndent())
        assertEquals("Phillies", r.card.subjectName)
        assertEquals(StatKey.TEAM_RUN_FIRST_INNING, r.card.stat)
        assertEquals(1, r.card.threshold) // "A RUN" -> 1
        assertEquals(5.0, r.card.displayedPoints, 1e-9)
    }

    @Test fun playerHomeRunNoBoost() {
        val r = parsed("""
            5.5 Points
            Francisco Alvarez
            C • #4
            WILL HIT A HOME RUN
        """.trimIndent())
        assertEquals("Francisco Alvarez", r.card.subjectName)
        assertEquals(StatKey.BATTER_HOME_RUNS, r.card.stat)
        assertEquals(1, r.card.threshold)
        assertEquals(5.5, r.card.displayedPoints, 1e-9)
    }

    // --- soccer (World Cup) cards ---

    @Test fun soccerShotOnTarget() {
        val r = parsed("""
            4.5 Points
            3 POINTS • 1.5X MULTIPLIER
            Leroy Sane
            MID • #19
            WILL HAVE A SHOT ON TARGET
        """.trimIndent())
        assertEquals("Leroy Sane", r.card.subjectName)
        assertEquals(StatKey.PLAYER_SHOTS_ON_TARGET, r.card.stat)
        assertEquals(1, r.card.threshold)          // "A" -> 1 -> Over 0.5
        assertEquals(0.5, r.card.overPoint, 1e-9)
        assertEquals(4.5, r.card.displayedPoints, 1e-9)
    }

    @Test fun soccerTwoPlusShots() {
        val r = parsed("4.5 Points\n3 POINTS • 1.5X MULTIPLIER\nLeon Goretzka\nMID • #8\nWILL ATTEMPT 2+ SHOTS")
        assertEquals(StatKey.PLAYER_SHOTS, r.card.stat)
        assertEquals(2, r.card.threshold)          // "2+" -> Over 1.5
        assertEquals(1.5, r.card.overPoint, 1e-9)
    }

    @Test fun soccerAttemptAShot() {
        val r = parsed("3 Points\nNico Schlotterbeck\nDEF • #15\nWILL ATTEMPT A SHOT")
        assertEquals(StatKey.PLAYER_SHOTS, r.card.stat)
        assertEquals(1, r.card.threshold)
    }

    @Test fun soccerTacklesParseButAreACoverageGap() {
        // Tackles parse to a Card, but PLAYER_TACKLES has no market -> priced as no-data.
        val r = parsed("3 Points\nAmad Diallo\nFWD • #15\nWILL RECORD 2+ TACKLES")
        assertEquals(StatKey.PLAYER_TACKLES, r.card.stat)
        assertEquals(2, r.card.threshold)
        assertTrue(r.card.stat.marketKeys.isEmpty())
    }

    // --- failure modes ---

    @Test fun missingPropLineFails() {
        val r = parser.parse("5.5 Points\nFrancisco Alvarez\nC • #4")
        assertIs<ParseOutcome.Unparsed>(r)
        assertTrue(r.reason.contains("WILL"))
    }

    @Test fun unrecognizedPropFails() {
        val r = parser.parse("4 Points\nMets\n34 - 41\nWILL DO SOMETHING WEIRD")
        assertIs<ParseOutcome.Unparsed>(r)
        assertTrue(r.reason.contains("unrecognized"))
    }

    @Test fun playerPropWithoutPlayerPillFails() {
        // home-run prop but only a team pill present
        val r = parser.parse("5 Points\nMets\n34 - 41\nWILL HIT A HOME RUN")
        assertIs<ParseOutcome.Unparsed>(r)
        assertTrue(r.reason.contains("expects a player"))
    }
}
