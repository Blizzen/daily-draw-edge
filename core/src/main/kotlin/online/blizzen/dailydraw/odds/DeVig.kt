package online.blizzen.dailydraw.odds

/**
 * Odds -> probability conversion and vig removal.
 *
 * Spike finding: card thresholds frequently land on *alternate* lines that books
 * post one-sided (Over only), so two-way de-vig isn't always possible. Rules:
 *   - both sides available  -> multiplicative de-vig (true two-way price)
 *   - Over only             -> raw implied minus a vig haircut, flagged low-confidence
 */
object DeVig {

    /** American odds -> implied probability (includes the book's vig). */
    fun americanToProb(american: Int): Double =
        if (american > 0) 100.0 / (american + 100.0)
        else (-american).toDouble() / (-american + 100.0)

    /** Multiplicative (proportional) de-vig of a two-way market. */
    fun twoWay(overAmerican: Int, underAmerican: Int): Double {
        val po = americanToProb(overAmerican)
        val pu = americanToProb(underAmerican)
        return po / (po + pu)
    }

    /**
     * One-sided estimate: strip an assumed half-hold from the raw Over implied.
     * Crude by necessity (no Under to anchor the vig); callers should treat this
     * as lower-confidence than [twoWay].
     *
     * @param assumedHold typical two-way overround for the market (~0.06 for
     *   MLB props); half is attributed to the Over side.
     */
    fun oneSided(overAmerican: Int, assumedHold: Double = DEFAULT_HOLD): Double {
        val raw = americanToProb(overAmerican)
        return (raw * (1.0 - assumedHold / 2.0)).coerceIn(0.0, 1.0)
    }

    const val DEFAULT_HOLD = 0.06
}
