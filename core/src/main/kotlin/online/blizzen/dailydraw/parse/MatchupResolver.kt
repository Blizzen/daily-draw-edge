package online.blizzen.dailydraw.parse

import online.blizzen.dailydraw.model.Game

/**
 * Resolves the single game's two teams from OCR'd header text like
 * "NYM 34-41 TODAY 6:15 PM PHI 40-35". The left/first abbreviation is the away
 * team, the second is home (matches the Odds API away @ home ordering).
 *
 * Returns team nicknames (e.g. "Mets") — [online.blizzen.dailydraw.odds.OddsApiClient]
 * matches these as substrings of full feed names ("New York Mets").
 */
object MatchupResolver {

    fun resolve(ocrText: String): Game? {
        val upper = ocrText.uppercase()
        val hits = MLB_ABBREVIATIONS.keys
            .mapNotNull { abbr -> indexOfToken(upper, abbr)?.let { it to abbr } }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
        if (hits.size < 2) return null
        return Game(awayTeam = MLB_ABBREVIATIONS.getValue(hits[0]), homeTeam = MLB_ABBREVIATIONS.getValue(hits[1]))
    }

    /** Match an abbreviation as a standalone token (word-boundaried). */
    private fun indexOfToken(text: String, token: String): Int? {
        val re = Regex("(?<![A-Z])" + Regex.escape(token) + "(?![A-Z])")
        return re.find(text)?.range?.first
    }

    /** Standard MLB abbreviation -> nickname used for odds-feed matching. */
    val MLB_ABBREVIATIONS: Map<String, String> = mapOf(
        "ARI" to "Diamondbacks", "ATL" to "Braves", "BAL" to "Orioles", "BOS" to "Red Sox",
        "CHC" to "Cubs", "CWS" to "White Sox", "CIN" to "Reds", "CLE" to "Guardians",
        "COL" to "Rockies", "DET" to "Tigers", "HOU" to "Astros", "KC" to "Royals",
        "LAA" to "Angels", "LAD" to "Dodgers", "MIA" to "Marlins", "MIL" to "Brewers",
        "MIN" to "Twins", "NYM" to "Mets", "NYY" to "Yankees", "OAK" to "Athletics",
        "ATH" to "Athletics", "PHI" to "Phillies", "PIT" to "Pirates", "SD" to "Padres",
        "SF" to "Giants", "SEA" to "Mariners", "STL" to "Cardinals", "TB" to "Rays",
        "TEX" to "Rangers", "TOR" to "Blue Jays", "WSH" to "Nationals", "WAS" to "Nationals",
    )
}
