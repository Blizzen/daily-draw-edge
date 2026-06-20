package online.blizzen.dailydraw.app.capture

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal bridge between [CaptureService] (foreground, owns MediaProjection) and
 * the ViewModel. The service publishes recording lifecycle + the finished file.
 */
object CaptureBus {
    sealed interface State {
        data object Idle : State
        data object Recording : State
        data class Recorded(val filePath: String) : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
}
