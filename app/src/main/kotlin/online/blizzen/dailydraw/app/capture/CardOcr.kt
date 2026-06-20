package online.blizzen.dailydraw.app.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import online.blizzen.dailydraw.model.Card
import online.blizzen.dailydraw.model.Game
import online.blizzen.dailydraw.parse.MatchupResolver
import online.blizzen.dailydraw.parse.ParseOutcome
import online.blizzen.dailydraw.parse.PropParser

/** Result of OCR-ing + parsing a recorded hand. */
data class ScanResult(
    val cards: List<Card>,
    val game: Game?,
    val unparsedSamples: List<String>,
)

/**
 * Runs ML Kit OCR over sampled frames, parses each into a [Card], and dedupes to
 * the distinct hand. Also resolves the matchup from any frame's header text.
 */
class CardOcr(private val parser: PropParser = PropParser()) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(frames: List<Bitmap>): ScanResult {
        val byKey = LinkedHashMap<String, Card>()
        val unparsed = ArrayList<String>()
        var game: Game? = null

        for (frame in frames) {
            val text = recognize(frame)
            if (text.isBlank()) continue
            if (game == null) game = MatchupResolver.resolve(text)
            when (val outcome = parser.parse(text)) {
                is ParseOutcome.Parsed -> {
                    val c = outcome.card
                    val key = "${c.subjectName}|${c.stat}|${c.threshold}"
                    byKey.putIfAbsent(key, c)
                }
                is ParseOutcome.Unparsed -> if (unparsed.size < 10) unparsed += outcome.reason
            }
        }
        return ScanResult(byKey.values.toList(), game, unparsed)
    }

    private suspend fun recognize(bitmap: Bitmap): String =
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } catch (_: Throwable) {
            ""
        }
}
