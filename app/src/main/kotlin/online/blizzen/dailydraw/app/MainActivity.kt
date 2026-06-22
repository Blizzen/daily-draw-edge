package online.blizzen.dailydraw.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.blizzen.dailydraw.app.capture.CaptureService
import online.blizzen.dailydraw.model.HandResult
import online.blizzen.dailydraw.model.ProbMethod
import online.blizzen.dailydraw.model.RankedCard
import online.blizzen.dailydraw.odds.EventSummary

class MainActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()
    private lateinit var projectionManager: MediaProjectionManager

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == RESULT_OK && data != null) startCapture(res.resultCode, data)
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { launchProjection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent {
            MaterialTheme {
                AppScreen(vm, onRecord = ::requestRecord, onStop = ::stopCapture, onReset = vm::reset)
            }
        }
    }

    private fun requestRecord() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    private fun launchProjection() = projectionLauncher.launch(projectionManager.createScreenCaptureIntent())

    private fun startCapture(resultCode: Int, data: Intent) {
        val (w, h, dpi) = screenSpec()
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
            putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            putExtra(CaptureService.EXTRA_WIDTH, w)
            putExtra(CaptureService.EXTRA_HEIGHT, h)
            putExtra(CaptureService.EXTRA_DPI, dpi)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopCapture() {
        startService(Intent(this, CaptureService::class.java).apply { action = CaptureService.ACTION_STOP })
    }

    /** Screen width/height (even-rounded for the encoder) and density dpi. */
    private fun screenSpec(): Triple<Int, Int, Int> {
        val dm = resources.displayMetrics
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            dm.widthPixels to dm.heightPixels
        }
        return Triple(w and 1.inv(), h and 1.inv(), dm.densityDpi)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    vm: CaptureViewModel,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Daily Draw Edge") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = vm::setApiKey,
                label = { Text("Odds API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val s = state) {
                is UiState.Recording -> Button(onStop, Modifier.fillMaxWidth()) { Text("Stop & analyze") }
                is UiState.Processing -> { /* handled below */ Button({}, Modifier.fillMaxWidth(), enabled = false) { Text("Working…") } }
                else -> Button(onRecord, Modifier.fillMaxWidth()) { Text("Record cards") }
            }

            when (val s = state) {
                is UiState.Idle ->
                    Text("Tap Record, swipe through all 6 cards in Yahoo, then return and Stop.",
                        style = MaterialTheme.typography.bodyMedium)
                is UiState.Recording ->
                    Text("Recording… open Yahoo, swipe through all 6 cards, then come back and Stop.")
                is UiState.Processing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(s.step)
                }
                is UiState.PickMatch -> MatchPicker(s, onPick = vm::pickEvent)
                is UiState.Error -> {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text(s.message)
                        }
                    }
                    TextButton(onReset) { Text("Try again") }
                }
                is UiState.Results -> ResultsView(s, onReset)
            }
        }
    }
}

@Composable
private fun MatchPicker(s: UiState.PickMatch, onPick: (EventSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pick the match (${s.sport.name})", style = MaterialTheme.typography.titleSmall)
        Text("Soccer cards show flags, not text — tap the game these cards are from.",
            style = MaterialTheme.typography.bodySmall)
        s.events.forEach { e ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(e) }
                        .padding(12.dp)
                ) {
                    Text("${e.awayTeam} @ ${e.homeTeam}", style = MaterialTheme.typography.titleSmall)
                    Text(e.commenceTime, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ResultsView(s: UiState.Results, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.game?.let { Text("${it.awayTeam} @ ${it.homeTeam}", style = MaterialTheme.typography.titleMedium) }
        val r = s.result
        Text("Recommended picks (top ${HandResult.PICK_COUNT})", style = MaterialTheme.typography.titleSmall)
        r.ranked.forEachIndexed { i, rc -> CardRow(rc, recommended = i < HandResult.PICK_COUNT) }
        if (r.noData.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("No odds — your call:", style = MaterialTheme.typography.titleSmall)
            r.noData.forEach { CardRow(it, recommended = false) }
        }
        if (r.manualFillNeeded > 0) {
            Text("Only ${r.ranked.size} priced; pick ${r.manualFillNeeded} more from the no-odds cards.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onReset) { Text("New hand") }
    }
}

@Composable
private fun CardRow(rc: RankedCard, recommended: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (recommended) "★" else "  ", Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(rc.card.subjectName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rc.card.rawPropText ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val ev = rc.ev
                Text(if (ev != null) "EV %.2f".format(ev) else "—", style = MaterialTheme.typography.titleSmall)
                val pct = rc.estimate.prob
                Text(
                    buildString {
                        append(rc.card.displayedPoints.toString()).append(" pts")
                        if (pct != null) append(" · ").append("%.0f%%".format(pct * 100))
                        append(methodSuffix(rc.estimate.method))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun methodSuffix(m: ProbMethod) = when (m) {
    ProbMethod.ONE_SIDED_HAIRCUT -> " ~"
    ProbMethod.NONE -> ""
    ProbMethod.TWO_WAY_DEVIG -> ""
}
