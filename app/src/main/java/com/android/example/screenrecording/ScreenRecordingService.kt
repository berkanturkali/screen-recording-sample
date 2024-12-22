package com.android.example.screenrecording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class ScreenRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "ScreenRecordingChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START_SCREEN_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_SCREEN_RECORDING"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_DATA = "RESULT_DATA"
    }

    private val androidVersion = Build.VERSION.SDK_INT
    private val androidVersion24 = Build.VERSION_CODES.N
    private val androidVersion25 = Build.VERSION_CODES.N_MR1
    private val androidVersion29 = Build.VERSION_CODES.Q

    private val androidVersionIs24Or25Or29
        get() = androidVersion == androidVersion24 ||
                androidVersion == androidVersion25 ||
                androidVersion == androidVersion29


    private var mediaProjection: MediaProjection? = null
    private lateinit var mediaCodec: MediaCodec
    private lateinit var mediaMuxer: MediaMuxer
    private var virtualDisplay: VirtualDisplay? = null
    private var videoTrackIndex: Int = -1
    private var isMuxerStarted = false
    private var isRecording = false
    override fun onBind(intent: Intent?): IBinder? = null
    private var recordingDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private lateinit var mediaRecorder: MediaRecorder

    private val recordingScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData: Intent = intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording(resultCode, resultData)
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

    private fun startRecording(resultCode: Int, resultData: Intent) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

        //Somehow the app crashing If I don't register a callback
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
            }
        }, Handler(Looper.getMainLooper()))

        val (width, height, densityDp) = getScreenMetrics()

        val outputPath = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val moviesDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "")
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            "${moviesDir.absolutePath}/screen_recording.mp4"
        } else {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                ""
            )
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }
            "${moviesDir.absolutePath}/screen_recording.mp4"
        }

        if(!androidVersionIs24Or25Or29) {
            mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        setupVideoEncoder(outputPath, width, height)


        val surface = if (androidVersionIs24Or25Or29) {
            mediaRecorder.surface
        } else {
            mediaCodec.createInputSurface()
        }
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            width,
            height,
            densityDp,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        isRecording = true
        if (androidVersionIs24Or25Or29) {
            mediaRecorder.start()
        } else {
            mediaCodec.start()
        }

        Log.i(TAG, "VirtualDisplay created with MediaCodec surface")

        if (!androidVersionIs24Or25Or29) {
            recordingScope.launch {
                writeEncodedData()
            }
        }

    }

    private val TAG = "ScreenRecordingService"

    private fun setupVideoEncoder(path: String, width: Int, height: Int) {
        if (androidVersionIs24Or25Or29) {
            mediaRecorder = MediaRecorder()
            mediaRecorder.apply {
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setOutputFile(path)
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                mediaRecorder.setVideoSize(1920, 1080) // Set resolution
                mediaRecorder.setVideoFrameRate(30)    // Set frame rate
                mediaRecorder.setVideoEncodingBitRate(5000000) // Set bitrate
            }
            mediaRecorder.prepare()
        } else {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val videoFormat =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, 8000000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    )
                }
            mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private suspend fun writeEncodedData() {
        val bufferInfo = MediaCodec.BufferInfo()

        withContext(recordingDispatcher) {
            while (isRecording) {
                try {
                    val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000)

                    if (outputBufferIndex >= 0) {
                        val encodedData = mediaCodec.getOutputBuffer(outputBufferIndex) ?: continue
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        if (isMuxerStarted) {
                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }

                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!isMuxerStarted) {
                            videoTrackIndex = mediaMuxer.addTrack(mediaCodec.outputFormat)
                            startMuxer()
                        }
                    } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Thread.sleep(10)
                    }
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Thread interrupted, exiting")
                    break
                }
            }
        }
    }

    private fun startMuxer() {
        mediaMuxer.start()
        isMuxerStarted = true
    }

    fun stopRecording() {

        isRecording = false
        recordingScope.cancel()

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null
        if (androidVersionIs24Or25Or29) {
            mediaRecorder.stop()
            mediaRecorder.reset()
            mediaRecorder.release()
        } else {
            try {
                mediaCodec.stop()
                mediaCodec.release()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "stopRecording: here")
                e.printStackTrace()
            }
        }


        if (isMuxerStarted) {
            mediaMuxer.stop()
            mediaMuxer.release()
            isMuxerStarted = false
        }
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