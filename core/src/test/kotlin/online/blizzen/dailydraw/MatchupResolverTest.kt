package online.blizzen.dailydraw

import online.blizzen.dailydraw.parse.MatchupResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchupResolverTest {
    @Test fun resolvesHeaderAwayThenHome() {
        val g = MatchupResolver.resolve("NYM 34-41 TODAY 6:15 PM PHI 40-35")
        assertEquals("Mets", g?.awayTeam)
        assertEquals("Phillies", g?.homeTeam)
    }

    @Test fun nullWhenFewerThanTwoTeams() {
        assertNull(MatchupResolver.resolve("NYM 34-41 TODAY 6:15 PM"))
    }

    @Test fun ignoresSubstringFalsePositives() {
        // "SD" must not match inside other words; only standalone tokens count.
        val g = MatchupResolver.resolve("Wednesday SEA vs TEX tonight")
        assertEquals("Mariners", g?.awayTeam)
        assertEquals("Rangers", g?.homeTeam)
    }
}
