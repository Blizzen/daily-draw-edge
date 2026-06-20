package online.blizzen.dailydraw.app.capture

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever

/**
 * Samples frames from a recorded mp4 at a fixed cadence. Downstream OCR + parse
 * drops blurry/partial frames (low parse confidence) and dedupes to the 6 cards,
 * so we don't need motion detection here.
 */
class FrameExtractor(private val fps: Double = 5.0) {

    fun extract(filePath: String): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) return emptyList()

            val stepUs = (1_000_000.0 / fps).toLong()
            val frames = ArrayList<Bitmap>()
            var tUs = 0L
            val endUs = durationMs * 1000
            while (tUs < endUs) {
                retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { frames += it }
                tUs += stepUs
            }
            frames
        } catch (_: Throwable) {
            emptyList()
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }
}
