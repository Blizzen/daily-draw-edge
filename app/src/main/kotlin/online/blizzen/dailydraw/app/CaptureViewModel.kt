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
import online.blizzen.dailydraw.model.Game
import online.blizzen.dailydraw.model.HandResult
import online.blizzen.dailydraw.odds.OddsApiClient
import online.blizzen.dailydraw.rank.Ranker

sealed interface UiState {
    data object Idle : UiState
    data object Recording : UiState
    data class Processing(val step: String) : UiState
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
                    _ui.value = UiState.Error("No cards recognized (parsed 0). Unparsed: ${scan.unparsedSamples.take(3)}")
                    return@launch
                }

                val key = _apiKey.value.trim()
                if (key.isEmpty()) { _ui.value = UiState.Error("Enter an Odds API key to price cards (${scan.cards.size} cards read)"); return@launch }
                val game = scan.game ?: run {
                    _ui.value = UiState.Error("Couldn't read the matchup header (${scan.cards.size} cards read)")
                    return@launch
                }

                _ui.value = UiState.Processing("Fetching odds for ${game.awayTeam} @ ${game.homeTeam}…")
                val result = withContext(Dispatchers.IO) {
                    val client = OddsApiClient(key)
                    val event = client.findEvent(game) ?: error("No odds event for ${game.awayTeam} @ ${game.homeTeam}")
                    val markets = scan.cards.flatMap { it.stat.marketKeys }.toSet()
                    val odds = client.eventOdds(event.id, markets)
                    Ranker().rank(scan.cards, odds)
                }
                _ui.value = UiState.Results(game, result, scan.unparsedSamples)
            } catch (t: Throwable) {
                _ui.value = UiState.Error(t.message ?: "processing failed")
            }
        }
    }

    companion object { private const val KEY_API = "odds_api_key" }
}
