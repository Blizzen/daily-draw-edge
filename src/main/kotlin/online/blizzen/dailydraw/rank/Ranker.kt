package online.blizzen.dailydraw.rank

import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.HandResult
import online.blizzen.dailydraw.model.RankedCard
import online.blizzen.dailydraw.odds.EventOdds
import online.blizzen.dailydraw.odds.ProbabilityEstimator

/**
 * Computes EV per card, ranks priced cards by EV (desc), and surfaces unpriced
 * cards separately so the user fills any shortfall manually — never fabricating
 * an EV for a card we couldn't price.
 */
class Ranker(private val estimator: ProbabilityEstimator = ProbabilityEstimator()) {

    fun rank(hand: List<Card>, odds: EventOdds): HandResult {
        val evaluated = hand.map { RankedCard(it, estimator.estimate(it, odds)) }
        val ranked = evaluated.filter { it.hasData }.sortedByDescending { it.ev }
        val noData = evaluated.filter { !it.hasData }
        return HandResult(ranked = ranked, noData = noData)
    }
}
