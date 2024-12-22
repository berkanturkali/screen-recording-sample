package com.android.example.screenrecording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.example.screenrecording.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var mediaProjection: MediaProjection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                // Permissions granted, proceed to request screen recording permission
                requestScreenRecordingPermission()
            } else {
                // Handle permission denial
                showPermissionDeniedMessage()
            }
        }
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupScreenCaptureLauncher()

        binding.startBtn.setOnClickListener {
            requestScreenRecordingPermission()
        }

        binding.stopBtn.setOnClickListener {
            Log.i(TAG, "onCreate: here")
            stopScreenRecording()
        }

        binding.playSoundBtn.setOnClickListener {
            val sound = MediaActionSound()
            sound.play(MediaActionSound.START_VIDEO_RECORDING)
        }
    }

    private val TAG = "MainActivity"

    private fun requestPermissionsIfNecessary() {
        val requiredPermissions = arrayOf(
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // If all permissions are granted, proceed to request screen recording permission
            requestScreenRecordingPermission()
        }
    }

    fun setupScreenCaptureLauncher() {
        screenCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    startScreenRecordingService(result.resultCode, result.data!!)
                }
            }
    }

    private fun startScreenRecordingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenRecordingService::class.java)
        intent.action = ScreenRecordingService.ACTION_START
        intent.putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
        intent.putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestScreenRecordingPermission() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }

    private fun stopScreenRecording() {
        val intent = Intent(this, ScreenRecordingService::class.java)
        intent.action = ScreenRecordingService.ACTION_STOP
        startService(intent)
    }


    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Permissions are required for screen recording", Toast.LENGTH_SHORT).show()
    }

    private fun showScreenCaptureDeniedMessage() {
        Toast.makeText(this, "Screen capture permission was denied", Toast.LENGTH_SHORT).show()
    }

}