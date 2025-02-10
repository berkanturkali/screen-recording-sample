package com.android.example.screenrecording.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.core.app.NotificationCompat
import com.android.example.screenrecording.R
import com.android.example.screenrecording.model.ScreenRecordingServiceInteractionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "ScreenRecordingChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START_SCREEN_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_SCREEN_RECORDING"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        const val OUTPUT_PATH = "OUTPUT_PATH"
        private const val MAX_RECORDING_TIME_SECONDS = 60
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false

    private var timeInSeconds = 0
    private var timerJob: Job? = null

    private val binder = ScreenRecordingBinder()

    private lateinit var screenRecordingServiceInteractionListener: ScreenRecordingServiceInteractionListener

    fun setScreenRecordingServiceInteractionListener(listener: ScreenRecordingServiceInteractionListener) {
        screenRecordingServiceInteractionListener = listener
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private lateinit var mediaRecorder: MediaRecorder

    private val recordingScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData: Intent = intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                val outputPath = intent.getStringExtra(OUTPUT_PATH)!!
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification(formatTime(timeInSeconds)))
                startRecording(resultCode, resultData, outputPath)
                startTimer()
            }

            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_recording_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.screen_recording_notification_channel_description)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(timeText: String): Notification {
        val stopIntent = Intent(this, ScreenRecordingService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_recording_notification_title))
            .setContentText(timeText)
            .setSmallIcon(R.drawable.ic_stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_stop,
                getString(R.string.screen_recording_notification_stop_action),
                stopPendingIntent
            )
            .build()

    }

    private fun startRecording(resultCode: Int, resultData: Intent, outputPath: String) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {}
        }, Handler(Looper.getMainLooper()))

        val (width, height, densityDp) = getScreenMetrics()



        setupVideoEncoder(outputPath, width, height)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            width,
            height,
            densityDp,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null
        )

        isRecording = true
        mediaRecorder.start()

    }

    private fun setupVideoEncoder(path: String, width: Int, height: Int) {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(baseContext)
        } else {
            MediaRecorder()
        }
        mediaRecorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(path)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(60)
            setVideoEncodingBitRate(8000000)
        }
        mediaRecorder.prepare()
    }

    private fun stopRecording() {

        isRecording = false
        recordingScope.cancel()

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        mediaRecorder.stop()
        mediaRecorder.reset()
        mediaRecorder.release()
    }

    private fun getScreenMetrics(): Triple<Int, Int, Int> {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val width = bounds.width()
            val height = bounds.height()
            val densityDpi = resources.displayMetrics.densityDpi
            return Triple(width, height, densityDpi)
        } else {
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val densityDpi = displayMetrics.densityDpi
            return Triple(width, height, densityDpi)
        }
    }

    fun updateNotification(timeText: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(timeText))
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                if (timeInSeconds == MAX_RECORDING_TIME_SECONDS) {
                    stopTimer()
                    screenRecordingServiceInteractionListener.onTimerReachedMaxLimit()
                    return@launch
                }
                val time = formatTime(timeInSeconds)
                screenRecordingServiceInteractionListener.onTimeUpdate(time)
                updateNotification(time)
                timeInSeconds++
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timeInSeconds = 0
        screenRecordingServiceInteractionListener.onTimeUpdate(formatTime(timeInSeconds))
    }

    private fun formatTime(seconds: Int): String {
        val minutest = seconds / 60
        val remainingSeconds = seconds % 60
        return getString(R.string.timer_format, minutest, remainingSeconds)
    }

    inner class ScreenRecordingBinder : Binder() {
        fun getService(): ScreenRecordingService = this@ScreenRecordingService
    }

}