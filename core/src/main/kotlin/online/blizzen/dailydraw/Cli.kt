package online.blizzen.dailydraw

import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.Game
import online.blizzen.dailydraw.model.HandResult
import online.blizzen.dailydraw.model.ProbMethod
import online.blizzen.dailydraw.model.Sport
import online.blizzen.dailydraw.model.StatKey
import online.blizzen.dailydraw.odds.OddsApiClient
import online.blizzen.dailydraw.rank.Ranker

/**
 * Runnable smoke test for the odds/de-vig/rank core.
 *
 *   ./gradlew run --args="<oddsApiKey>"
 *
 * Uses a built-in demo hand (the real NYM@PHI cards from the spike screenshots),
 * looks up live odds, and prints the EV ranking. No Android required.
 */
fun main(args: Array<String>) {
    val apiKey = args.getOrNull(0) ?: run {
        System.err.println("usage: run --args=\"<oddsApiKey>\"")
        return
    }

    val soccer = args.getOrNull(1)?.lowercase() in setOf("wc", "soccer")
    val sport = if (soccer) Sport.WORLD_CUP else Sport.MLB
    val game: Game
    val hand: List<Card>
    if (soccer) {
        game = Game(awayTeam = "Iraq", homeTeam = "France")
        hand = listOf(
            Card("Kylian Mbappe", StatKey.PLAYER_SHOTS_ON_TARGET, threshold = 1, displayedPoints = 4.5, rawPropText = "WILL HAVE A SHOT ON TARGET"),
            Card("Ousmane Dembele", StatKey.PLAYER_SHOTS, threshold = 1, displayedPoints = 3.0, rawPropText = "WILL ATTEMPT A SHOT"),
            Card("Maxence Lacroix", StatKey.PLAYER_SHOTS, threshold = 2, displayedPoints = 5.5, rawPropText = "WILL ATTEMPT 2+ SHOTS"),
            Card("Adam Diallo", StatKey.PLAYER_TACKLES, threshold = 2, displayedPoints = 4.0, rawPropText = "WILL RECORD 2+ TACKLES"),
        )
    } else {
        game = Game(awayTeam = "Mets", homeTeam = "Phillies")
        hand = listOf(
            Card("Mets", StatKey.TEAM_RUNS, threshold = 4, displayedPoints = 5.25, rawPropText = "WILL SCORE 4+ RUNS"),
            Card("Justin Crawford", StatKey.BATTER_HITS_RUNS_RBIS, threshold = 3, displayedPoints = 6.75, rawPropText = "WILL RECORD 3+ COMBINED HITS + RUNS + RBIS"),
            Card("Phillies", StatKey.TEAM_RUN_FIRST_INNING, threshold = 1, displayedPoints = 5.0, rawPropText = "WILL SCORE A RUN IN THE 1ST INNING"),
            Card("Francisco Alvarez", StatKey.BATTER_HOME_RUNS, threshold = 1, displayedPoints = 5.5, rawPropText = "WILL HIT A HOME RUN"),
        )
    }

    val client = OddsApiClient(apiKey, sport)
    val event = client.findEvent(game) ?: run {
        System.err.println("No event found for ${game.awayTeam} @ ${game.homeTeam} today.")
        return
    }
    println("Event: ${event.awayTeam} @ ${event.homeTeam}  (${event.commenceTime})")

    val markets = hand.flatMap { it.stat.marketKeys }.toSet()
    val odds = client.eventOdds(event.id, markets)
    val result = Ranker().rank(hand, odds)

    printResult(result)
    println()
    println("API credits remaining: ${client.lastRequestsRemaining ?: "?"}")
}

private fun printResult(result: HandResult) {
    println()
    println("%-32s %5s %8s %7s  %s".format("CARD", "PTS", "P(YES)", "EV", "SOURCE"))
    println("-".repeat(92))
    result.ranked.forEachIndexed { i, rc ->
        val pick = if (i < HandResult.PICK_COUNT) "*" else " "
        println(
            "%s%-31s %5.2f %7.1f%% %7.2f  %s".format(
                pick, label(rc.card),
                rc.card.displayedPoints, rc.estimate.prob!! * 100, rc.ev!!,
                methodTag(rc.estimate.method) + " " + rc.estimate.detail,
            )
        )
    }
    result.noData.forEach { rc ->
        println(
            "%s%-31s %5.2f %8s %7s  %s".format(
                " ", label(rc.card),
                rc.card.displayedPoints, "--", "--", "NO DATA: " + rc.estimate.detail,
            )
        )
    }
    println("-".repeat(92))
    println("Recommended (*): ${result.recommended.joinToString { it.card.subjectName }}")
    if (result.manualFillNeeded > 0) {
        println("Only ${result.ranked.size} priced card(s); pick ${result.manualFillNeeded} more manually from NO DATA cards.")
    }
}

private fun methodTag(m: ProbMethod) = when (m) {
    ProbMethod.TWO_WAY_DEVIG -> "[2-way]"
    ProbMethod.ONE_SIDED_HAIRCUT -> "[1-side]"
    ProbMethod.NONE -> "[none]"
}

private fun shortProp(text: String?): String =
    (text ?: "").removePrefix("WILL ").lowercase().take(28)

private fun label(card: Card): String {
    val full = card.subjectName + " (" + shortProp(card.rawPropText) + ")"
    return if (full.length <= 31) full else full.take(30) + "…"
}
