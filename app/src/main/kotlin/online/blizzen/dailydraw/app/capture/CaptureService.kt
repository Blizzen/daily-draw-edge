package online.blizzen.dailydraw.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File

/**
 * Foreground service that records the screen via MediaProjection -> MediaRecorder
 * into an mp4. Android 10+ requires screen capture to run inside a
 * mediaProjection-typed foreground service.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        try {
            startForeground(NOTIF_ID, buildNotification())

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                ?: error("missing projection result data")
            val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
            val height = intent.getIntExtra(EXTRA_HEIGHT, 2340)
            val dpi = intent.getIntExtra(EXTRA_DPI, 420)

            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(resultCode, data)
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { cleanup() }
            }, null)
            projection = proj

            val file = File(cacheDir, "draw_capture.mp4")
            outputFile = file
            recorder = newRecorder(file, width, height).also { it.prepare() }

            virtualDisplay = proj.createVirtualDisplay(
                "DailyDrawCapture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface, null, null,
            )
            recorder!!.start()
            CaptureBus.state.value = CaptureBus.State.Recording
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            CaptureBus.state.value = CaptureBus.State.Failed(t.message ?: "capture start failed")
            cleanup()
            stopSelf()
        }
    }

    private fun stop() {
        val file = outputFile
        try {
            recorder?.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "recorder.stop failed", t)
        }
        cleanup()
        if (file != null && file.exists() && file.length() > 0) {
            CaptureBus.state.value = CaptureBus.State.Recorded(file.absolutePath)
        } else {
            CaptureBus.state.value = CaptureBus.State.Failed("no recording produced")
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        recorder = null
        projection = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(file: File, width: Int, height: Int): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(width, height)
        r.setVideoFrameRate(30)
        r.setVideoEncodingBitRate(8_000_000)
        r.setOutputFile(file.absolutePath)
        return r
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Daily Draw Edge")
            .setContentText("Recording your cards…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "CaptureService"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 42

        const val ACTION_START = "online.blizzen.dailydraw.START"
        const val ACTION_STOP = "online.blizzen.dailydraw.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DPI = "dpi"
    }
}
