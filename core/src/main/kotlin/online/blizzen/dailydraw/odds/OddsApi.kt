package online.blizzen.dailydraw.odds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class EventSummary(
    val id: String,
    @SerialName("commence_time") val commenceTime: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
)

@Serializable
data class EventOdds(
    val id: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String,
    val bookmakers: List<Bookmaker> = emptyList(),
)

@Serializable
data class Bookmaker(
    val key: String,
    val title: String = "",
    val markets: List<Market> = emptyList(),
)

@Serializable
data class Market(
    val key: String,
    val outcomes: List<Outcome> = emptyList(),
)

@Serializable
data class Outcome(
    val name: String,            // "Over" / "Under" / "Yes" / "No"
    val price: Int,              // american odds (oddsFormat=american)
    val point: Double? = null,   // the line
    val description: String? = null, // player or team name for props/team-totals
)

/**
 * Thin client for The Odds API v4. Player props require the per-event endpoint.
 * The API key is passed per call and never stored.
 */
class OddsApiClient(
    private val apiKey: String,
    private val region: String = "us",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://api.the-odds-api.com/v4/sports/baseball_mlb"

    var lastRequestsRemaining: String? = null
        private set

    // HttpURLConnection so :core stays portable across the JVM CLI and Android
    // (Android has no java.net.http package).
    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = conn.responseCode
            lastRequestsRemaining = conn.getHeaderField("x-requests-remaining")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            require(code in 200..299) { "Odds API $code: ${body.take(300)}" }
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun listEvents(): List<EventSummary> =
        json.decodeFromString(get("$base/events?apiKey=$apiKey"))

    /** Find an event by team names (matches either order, substring-tolerant). */
    fun findEvent(game: online.blizzen.dailydraw.model.Game): EventSummary? =
        listEvents().firstOrNull { e ->
            val text = "${e.awayTeam} ${e.homeTeam}".lowercase()
            text.contains(game.awayTeam.lowercase()) && text.contains(game.homeTeam.lowercase())
        }

    fun eventOdds(eventId: String, marketKeys: Collection<String>): EventOdds {
        val markets = marketKeys.joinToString(",")
        val url = "$base/events/$eventId/odds?apiKey=$apiKey&regions=$region" +
            "&markets=$markets&oddsFormat=american"
        return json.decodeFromString(get(url))
    }
}
