package online.blizzen.dailydraw.model

/**
 * The stat a card's prop is about, plus the Odds API market keys it maps to.
 * Markets are tried in order (standard line first, then alternate).
 *
 * Threshold props are "N+" yes/no events, scored against an Over (N - 0.5) line.
 * Markets with an empty list are known coverage gaps (e.g. per-team 1st-inning
 * run) and always resolve to "no data" -> manual judgement.
 */
enum class StatKey(
    val marketKeys: List<String>,
    val subjectIsPlayer: Boolean,
) {
    TEAM_RUNS(listOf("team_totals", "alternate_team_totals"), subjectIsPlayer = false),
    BATTER_HOME_RUNS(listOf("batter_home_runs", "batter_home_runs_alternate"), subjectIsPlayer = true),
    BATTER_HITS_RUNS_RBIS(
        listOf("batter_hits_runs_rbis", "batter_hits_runs_rbis_alternate"),
        subjectIsPlayer = true,
    ),

    /** No per-team 1st-inning market exists in The Odds API (spike finding). */
    TEAM_RUN_FIRST_INNING(emptyList(), subjectIsPlayer = false),
}

/** The two teams in the single game a Daily Draw is tied to. */
data class Game(val awayTeam: String, val homeTeam: String)

/**
 * One parsed card.
 *
 * @param subjectName team or player as it should match the odds feed
 *   (team short name like "Mets" or full player name "Francisco Alvarez").
 * @param threshold the "N" in "N+" (1 for an anytime/yes-no prop).
 * @param displayedPoints the points shown on the card — already includes any
 *   1.25x / 1.5x boost (e.g. 5.25 = 3.5 base x 1.5).
 */
data class Card(
    val subjectName: String,
    val stat: StatKey,
    val threshold: Int,
    val displayedPoints: Double,
    val rawPropText: String? = null,
) {
    /** The Over line that represents "threshold or more". "4+ runs" -> Over 3.5. */
    val overPoint: Double get() = threshold - 0.5
}

enum class ProbMethod { TWO_WAY_DEVIG, ONE_SIDED_HAIRCUT, NONE }

/** Probability estimate for a single card, with provenance for transparency. */
data class ProbEstimate(
    val prob: Double?,
    val method: ProbMethod,
    val books: List<String> = emptyList(),
    val detail: String = "",
) {
    companion object {
        fun none(detail: String) = ProbEstimate(null, ProbMethod.NONE, detail = detail)
    }
}

/** A card after probability + EV are computed. */
data class RankedCard(
    val card: Card,
    val estimate: ProbEstimate,
) {
    val ev: Double? get() = estimate.prob?.let { card.displayedPoints * it }
    val hasData: Boolean get() = ev != null
}

/** The final recommendation for a hand. */
data class HandResult(
    val ranked: List<RankedCard>,   // cards with data, sorted by EV desc
    val noData: List<RankedCard>,   // cards we couldn't price
) {
    /** Top-4 recommended picks from priced cards. */
    val recommended: List<RankedCard> get() = ranked.take(PICK_COUNT)

    /** How many more picks must be filled manually from no-data cards. */
    val manualFillNeeded: Int get() = (PICK_COUNT - ranked.size).coerceAtLeast(0)

    companion object { const val PICK_COUNT = 4 }
}
