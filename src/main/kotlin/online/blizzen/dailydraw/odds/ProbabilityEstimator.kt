package online.blizzen.dailydraw.odds

import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.ProbEstimate
import online.blizzen.dailydraw.model.ProbMethod

/**
 * Turns a [Card] + the game's [EventOdds] into a [ProbEstimate].
 *
 * For each of the card's candidate markets (standard before alternate), it
 * collects every book's Over (and Under, if posted) at the card's line and
 * subject. If any book posts both sides -> two-way de-vig consensus. Otherwise
 * -> one-sided haircut consensus. First market that yields a price wins.
 */
class ProbabilityEstimator(private val assumedHold: Double = DeVig.DEFAULT_HOLD) {

    fun estimate(card: Card, odds: EventOdds): ProbEstimate {
        if (card.stat.marketKeys.isEmpty()) {
            return ProbEstimate.none("no market exists for ${card.stat} (coverage gap)")
        }
        for (marketKey in card.stat.marketKeys) {
            val priced = priceFromMarket(card, odds, marketKey)
            if (priced != null) return priced
        }
        return ProbEstimate.none("no book posted ${card.stat.marketKeys} for '${card.subjectName}' at ${card.overPoint}")
    }

    private fun priceFromMarket(card: Card, odds: EventOdds, marketKey: String): ProbEstimate? {
        data class Side(val over: Int?, val under: Int?)
        val perBook = LinkedHashMap<String, Side>()

        for (b in odds.bookmakers) {
            val market = b.markets.firstOrNull { it.key == marketKey } ?: continue
            var over: Int? = null
            var under: Int? = null
            for (oc in market.outcomes) {
                if (!subjectMatches(oc.description, card)) continue
                if (oc.point == null || kotlin.math.abs(oc.point - card.overPoint) > 0.01) continue
                when (oc.name.lowercase()) {
                    "over", "yes" -> over = oc.price
                    "under", "no" -> under = oc.price
                }
            }
            if (over != null || under != null) perBook[b.key] = Side(over, under)
        }
        if (perBook.isEmpty()) return null

        val twoWay = perBook.filter { it.value.over != null && it.value.under != null }
        if (twoWay.isNotEmpty()) {
            val probs = twoWay.map { DeVig.twoWay(it.value.over!!, it.value.under!!) }
            return ProbEstimate(
                prob = median(probs),
                method = ProbMethod.TWO_WAY_DEVIG,
                books = twoWay.keys.toList(),
                detail = "$marketKey, two-way de-vig across ${twoWay.size} book(s)",
            )
        }
        val oneSided = perBook.filter { it.value.over != null }
        if (oneSided.isNotEmpty()) {
            val probs = oneSided.map { DeVig.oneSided(it.value.over!!, assumedHold) }
            return ProbEstimate(
                prob = median(probs),
                method = ProbMethod.ONE_SIDED_HAIRCUT,
                books = oneSided.keys.toList(),
                detail = "$marketKey, one-sided (Over only) haircut across ${oneSided.size} book(s)",
            )
        }
        return null
    }

    private fun subjectMatches(description: String?, card: Card): Boolean {
        if (description == null) return false
        val d = description.lowercase()
        val s = card.subjectName.lowercase()
        // Player: card carries the full name. Team: card carries the short name
        // (e.g. "Mets") while the feed uses "New York Mets" -> substring covers both.
        return d.contains(s) || s.contains(d)
    }

    private fun median(xs: List<Double>): Double {
        val sorted = xs.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
