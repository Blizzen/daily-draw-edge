package online.blizzen.dailydraw.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.blizzen.dailydraw.app.capture.CaptureBus
import online.blizzen.dailydraw.app.capture.CardOcr
import online.blizzen.dailydraw.app.capture.FrameExtractor
import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.Game
import online.blizzen.dailydraw.model.HandResult
import online.blizzen.dailydraw.model.Sport
import online.blizzen.dailydraw.model.dominantSport
import online.blizzen.dailydraw.odds.EventSummary
import online.blizzen.dailydraw.odds.OddsApiClient
import online.blizzen.dailydraw.rank.Ranker

sealed interface UiState {
    data object Idle : UiState
    data object Recording : UiState
    data class Processing(val step: String) : UiState
    data class PickMatch(val sport: Sport, val events: List<EventSummary>) : UiState
    data class Results(
        val game: Game?,
        val result: HandResult,
        val unparsed: List<String>,
    ) : UiState
    data class Error(val message: String) : UiState
}

class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("ddx", Application.MODE_PRIVATE)

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui = _ui.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API, "") ?: "")
    val apiKey = _apiKey.asStateFlow()

    // Held between OCR and a manual match pick (soccer has no OCR-able matchup).
    private var pendingCards: List<Card> = emptyList()
    private var pendingSport: Sport = Sport.MLB
    private var pendingUnparsed: List<String> = emptyList()

    init {
        viewModelScope.launch {
            CaptureBus.state.collect { s ->
                when (s) {
                    is CaptureBus.State.Recording -> _ui.value = UiState.Recording
                    is CaptureBus.State.Recorded -> process(s.filePath)
                    is CaptureBus.State.Failed -> _ui.value = UiState.Error(s.message)
                    is CaptureBus.State.Idle -> Unit
                }
            }
        }
    }

    fun setApiKey(value: String) {
        _apiKey.value = value
        prefs.edit().putString(KEY_API, value).apply()
    }

    fun reset() {
        CaptureBus.state.value = CaptureBus.State.Idle
        _ui.value = UiState.Idle
    }

    private fun process(filePath: String) {
        viewModelScope.launch {
            try {
                _ui.value = UiState.Processing("Extracting frames…")
                val frames = withContext(Dispatchers.IO) { FrameExtractor().extract(filePath) }
                if (frames.isEmpty()) { _ui.value = UiState.Error("No frames in recording"); return@launch }

                _ui.value = UiState.Processing("Reading ${frames.size} frames…")
                val scan = withContext(Dispatchers.Default) { CardOcr().scan(frames) }
                if (scan.cards.isEmpty()) {
                    _ui.value = UiState.Error("No cards recognized. Unparsed: ${scan.unparsedSamples.take(3)}")
                    return@launch
                }
                val sport = scan.cards.dominantSport()
                    ?: run { _ui.value = UiState.Error("Couldn't tell the sport from ${scan.cards.size} cards"); return@launch }

                val key = _apiKey.value.trim()
                if (key.isEmpty()) { _ui.value = UiState.Error("Enter an Odds API key (${scan.cards.size} cards read)"); return@launch }

                pendingCards = scan.cards
                pendingSport = sport
                pendingUnparsed = scan.unparsedSamples

                // MLB: try to resolve the matchup from the OCR'd header.
                val game = scan.game
                if (game != null) {
                    _ui.value = UiState.Processing("Finding ${game.awayTeam} @ ${game.homeTeam}…")
                    val event = withContext(Dispatchers.IO) { OddsApiClient(key, sport).findEvent(game) }
                    if (event != null) { priceAndRank(key, sport, event); return@launch }
                }
                // Otherwise (soccer, or header miss): let the user pick today's match.
                _ui.value = UiState.Processing("Loading ${sport.name} fixtures…")
                val events = withContext(Dispatchers.IO) { OddsApiClient(key, sport).listEvents() }
                if (events.isEmpty()) { _ui.value = UiState.Error("No ${sport.name} fixtures found today"); return@launch }
                _ui.value = UiState.PickMatch(sport, events)
            } catch (t: Throwable) {
                _ui.value = UiState.Error(t.message ?: "processing failed")
            }
        }
    }

    fun pickEvent(event: EventSummary) {
        viewModelScope.launch {
            try {
                priceAndRank(_apiKey.value.trim(), pendingSport, event)
            } catch (t: Throwable) {
                _ui.value = UiState.Error(t.message ?: "pricing failed")
            }
        }
    }

    private suspend fun priceAndRank(key: String, sport: Sport, event: EventSummary) {
        _ui.value = UiState.Processing("Fetching odds for ${event.awayTeam} @ ${event.homeTeam}…")
        val result = withContext(Dispatchers.IO) {
            val client = OddsApiClient(key, sport)
            val markets = pendingCards.flatMap { it.stat.marketKeys }.toSet()
            val odds = client.eventOdds(event.id, markets)
            Ranker().rank(pendingCards, odds)
        }
        _ui.value = UiState.Results(Game(event.awayTeam, event.homeTeam), result, pendingUnparsed)
    }

    companion object { private const val KEY_API = "odds_api_key" }
}
