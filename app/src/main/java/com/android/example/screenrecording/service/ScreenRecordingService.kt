package com.android.example.screenrecording.service

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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class ScreenRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "ScreenRecordingChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START_SCREEN_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_SCREEN_RECORDING"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
        const val OUTPUT_PATH = "OUTPUT_PATH"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    private lateinit var mediaRecorder: MediaRecorder

    private val recordingScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData: Intent = intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                val outputPath = intent.getStringExtra(OUTPUT_PATH)!!
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording(resultCode, resultData, outputPath)
            }

            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Recording screen"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording in Progress")
            .setContentText("Your screen is being recorded.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
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
        Log.i(TAG, "VirtualDisplay created with MediaCodec surface")

    }

    private val TAG = "ScreenRecordingService"

    private fun setupVideoEncoder(path: String, width: Int, height: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = MediaRecorder(baseContext)
        } else {
            mediaRecorder = MediaRecorder()
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


}