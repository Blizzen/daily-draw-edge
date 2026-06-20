package online.blizzen.dailydraw.parse

import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.StatKey

enum class SubjectType { TEAM, PLAYER, UNKNOWN }

sealed class ParseOutcome {
    data class Parsed(val card: Card, val warnings: List<String> = emptyList()) : ParseOutcome()
    data class Unparsed(val rawText: String, val reason: String) : ParseOutcome()
}

/**
 * One entry of the stat dictionary: a phrase matcher -> StatKey. Ordered; first
 * match wins, so more specific phrases must precede general ones (e.g. "home run"
 * and "1st inning" before bare "runs"). [supported] = false marks a recognized
 * but v1-unsupported prop (soccer) so we fail with a clear reason, not silently.
 */
data class PropRule(
    val matcher: Regex,
    val stat: StatKey?,
    val supported: Boolean = true,
    val note: String = "",
)

/**
 * Turns the OCR text of a single card into a [Card]. Pure and deterministic so it
 * can be unit-tested against real screenshots without OCR or Android.
 */
class PropParser(private val rules: List<PropRule> = DEFAULT_RULES) {

    fun parse(ocrText: String): ParseOutcome {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val upper = lines.map { it.uppercase() }

        val points = extractDisplayedPoints(upper)
            ?: return ParseOutcome.Unparsed(ocrText, "no '<number> Points' found")

        val propText = extractPropText(lines)
            ?: return ParseOutcome.Unparsed(ocrText, "no 'WILL ...' prop line found")

        val rule = rules.firstOrNull { it.matcher.containsMatchIn(propText.uppercase()) }
            ?: return ParseOutcome.Unparsed(ocrText, "unrecognized prop: \"$propText\"")
        if (!rule.supported || rule.stat == null) {
            return ParseOutcome.Unparsed(ocrText, "prop not supported in v1${if (rule.note.isNotEmpty()) " (${rule.note})" else ""}: \"$propText\"")
        }

        val threshold = extractThreshold(propText)
        val warnings = mutableListOf<String>()
        val finalThreshold = threshold ?: run {
            warnings += "no threshold found in \"$propText\"; defaulting to 1"
            1
        }

        val playerName = detectPlayerName(lines, upper)
        val teamName = detectTeamName(lines, upper)
        val detected = when {
            playerName != null -> SubjectType.PLAYER
            teamName != null -> SubjectType.TEAM
            else -> SubjectType.UNKNOWN
        }

        val subjectName: String = if (rule.stat.subjectIsPlayer) {
            playerName ?: return ParseOutcome.Unparsed(ocrText, "${rule.stat} expects a player but no player pill (NAME / POS · #num) found")
        } else {
            teamName ?: return ParseOutcome.Unparsed(ocrText, "${rule.stat} expects a team but no team pill (NAME / W - L) found")
        }
        if (detected != SubjectType.UNKNOWN &&
            (detected == SubjectType.PLAYER) != rule.stat.subjectIsPlayer
        ) {
            warnings += "subject type ($detected) disagrees with prop ${rule.stat}"
        }

        val boost = extractBoost(upper)
        if (boost != null) {
            val expected = boost.base * boost.multiplier
            if (kotlin.math.abs(expected - points) > 0.011) {
                warnings += "displayed points $points != base ${boost.base} x ${boost.multiplier} (=$expected)"
            }
        }

        return ParseOutcome.Parsed(
            Card(
                subjectName = subjectName,
                stat = rule.stat,
                threshold = finalThreshold,
                displayedPoints = points,
                rawPropText = propText,
            ),
            warnings,
        )
    }

    // --- field extractors ---

    private val pointsRe = Regex("""([0-9]+(?:\.[0-9]+)?)\s*POINTS""")
    private val multiplierRe = Regex("""([0-9]+(?:\.[0-9]+)?)\s*POINTS\s*[^0-9]*([0-9]+(?:\.[0-9]+)?)\s*X\s*MULTIPLIER""")
    // Player pill "CF · #2": letter-led position code, "#", and a "•/·/." separator
    // (NOT the "-" used by team records like "34 - 41").
    private val posPillRe = Regex("""^(?=.*[A-Z])[A-Z0-9]{1,3}\s*[•·.]\s*#\s*[0-9]{1,3}$""")
    private val recordRe = Regex("""^[0-9]{1,3}\s*-\s*[0-9]{1,3}$""")
    private val thresholdRe = Regex("""([0-9]+)\s*\+""")
    private val singularRe = Regex("""\b(A|AN)\b""")

    /** Displayed total = the largest "<n> Points" value (boost subtitle is smaller). */
    private fun extractDisplayedPoints(upper: List<String>): Double? =
        upper.flatMap { line -> pointsRe.findAll(line).map { it.groupValues[1].toDouble() } }
            .maxOrNull()

    private data class Boost(val base: Double, val multiplier: Double)

    private fun extractBoost(upper: List<String>): Boost? =
        upper.firstNotNullOfOrNull { line ->
            multiplierRe.find(line)?.let { Boost(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
        }

    /** Join the "WILL ..." line with following wrapped lines into one prop string. */
    private fun extractPropText(lines: List<String>): String? {
        val start = lines.indexOfFirst { it.uppercase().startsWith("WILL ") }
        if (start < 0) return null
        return lines.subList(start, lines.size).joinToString(" ")
            .replace(Regex("""\s+"""), " ").trim()
    }

    private fun extractThreshold(propText: String): Int? {
        thresholdRe.find(propText)?.let { return it.groupValues[1].toInt() }
        if (singularRe.containsMatchIn(propText.uppercase())) return 1
        return null
    }

    /** Player pill: NAME line directly above a "POS · #num" line. */
    private fun detectPlayerName(lines: List<String>, upper: List<String>): String? {
        val idx = upper.indexOfFirst { posPillRe.matches(it) }
        return if (idx > 0) lines[idx - 1] else null
    }

    /** Team pill: NAME line directly above a "W - L" record line. */
    private fun detectTeamName(lines: List<String>, upper: List<String>): String? {
        val idx = upper.indexOfFirst { recordRe.matches(it) }
        return if (idx > 0) lines[idx - 1] else null
    }

    companion object {
        /** Order matters: specific phrases before general ones. */
        val DEFAULT_RULES: List<PropRule> = listOf(
            PropRule(Regex("1ST INNING|FIRST INNING"), StatKey.TEAM_RUN_FIRST_INNING),
            PropRule(Regex("HITS.*RUNS.*RBIS|HITS \\+ RUNS \\+ RBIS"), StatKey.BATTER_HITS_RUNS_RBIS),
            PropRule(Regex("HOME RUN"), StatKey.BATTER_HOME_RUNS),
            PropRule(Regex("\\bRUNS?\\b"), StatKey.TEAM_RUNS),
            // recognized but unsupported in MLB-first v1 (soccer):
            PropRule(Regex("TACKLE|SHOT ON TARGET|\\bSHOTS?\\b"), null, supported = false, note = "soccer"),
        )
    }
}
