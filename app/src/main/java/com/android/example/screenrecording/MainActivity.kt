package com.android.example.screenrecording

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.android.example.screenrecording.databinding.ActivityMainBinding
import com.android.example.screenrecording.model.ScreenRecordButtonActions
import com.android.example.screenrecording.service.ScreenRecordingService
import com.android.example.screenrecording.viewmodel.MainActivityViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var navController: NavController

    private val viewModel by viewModels<MainActivityViewModel>()

    private var screenWidth = 0
    private var screenHeight = 0

    private var fadeJob: Job? = null

    private var recordOutputPath: String = getOutputPath()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupNavigation()
        setClickListeners()
        startOpacityTimer()
        subscribeObservers()

        // Setting this from themes does not work and this shit is deprecated in Android 14+
        // Changing a simple stupid status bar color should not be this challenging
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary)

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val allGranted = permissions.all { it.value }
                if (allGranted) {
                    requestScreenRecordingPermission()
                } else {
                    showPermissionDeniedMessage()
                }
            }
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupScreenCaptureLauncher()
    }

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
            requestScreenRecordingPermission()
        }
    }

    private fun setupScreenCaptureLauncher() {
        screenCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    startScreenRecordingService(result.resultCode, result.data!!)
                    viewModel.setScreenRecordButtonAction(ScreenRecordButtonActions.ACTION_START)
                }
            }
    }

    private fun startScreenRecordingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenRecordingService::class.java)
        intent.action = ScreenRecordingService.ACTION_START
        intent.putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
        intent.putExtra(ScreenRecordingService.EXTRA_RESULT_DATA, data)
        intent.putExtra(ScreenRecordingService.OUTPUT_PATH, recordOutputPath)
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
        Toast.makeText(this, "Permissions are required for screen recording", Toast.LENGTH_SHORT)
            .show()
    }

    private fun showScreenCaptureDeniedMessage() {
        Toast.makeText(this, "Screen capture permission was denied", Toast.LENGTH_SHORT).show()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.titleTv.text = destination.label

            binding.backIv.isVisible = destination.id != R.id.fragmentA

            binding.moveNextBtn.visibility =
                if (destination.id != R.id.fragmentC) View.VISIBLE else View.INVISIBLE

        }
    }

    private fun subscribeObservers() {
        viewModel.screenRecordButtonAction.observe(this) { action ->
            when (action) {
                ScreenRecordButtonActions.ACTION_START -> {
                    animateStartScreenRecordingButtonIcon(R.drawable.ic_stop)
                    startPulsingAnimationForRecordIndicator()
                }

                ScreenRecordButtonActions.ACTION_STOP -> {
                    animateStartScreenRecordingButtonIcon(R.drawable.ic_play)
                    stopPulsingAnimationForRecordIndicator()
                    showPreviewDialog()
                }
            }
        }

        viewModel.time.observe(this) { time ->
            binding.draggableScreenCaptureLayout.timerTv.text = time
        }

        viewModel.statusBarColor.observe(this) { color ->
            window.statusBarColor = ContextCompat.getColor(this, color)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setClickListeners() {
        binding.apply {
            backIv.setOnClickListener {
                navController.navigateUp()
            }
            moveNextBtn.setOnClickListener {
                navController.navigate(viewModel.navigationActionId)
            }

            draggableScreenCaptureLayout.startStopRecordingBtn.setOnClickListener {
                resetOpacity()
                viewModel.screenRecordButtonAction.value?.let { action ->
                    if (action == ScreenRecordButtonActions.ACTION_START) {
                        stopScreenRecording()
                        viewModel.setScreenRecordButtonAction(ScreenRecordButtonActions.ACTION_STOP)
                    } else {
                        requestScreenRecordingPermission()
                    }
                } ?: kotlin.run {
                    requestScreenRecordingPermission()
                }

                lifecycleScope.launch {
                    delay(1000)
                    startOpacityTimer()
                }
            }

            draggableScreenCaptureLayout.root.setOnTouchListener { v, event ->

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        resetOpacity()
                        viewModel.dX = v.x - event.rawX
                        viewModel.dY = v.y - event.rawY
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val maxX = (screenWidth - v.width).toFloat().coerceAtLeast(0f)
                        val maxY = (screenHeight - v.height).toFloat().coerceAtLeast(0f)

                        val newX = (event.rawX + viewModel.dX).coerceIn(0f, maxX)
                        val newY = (event.rawY + viewModel.dY).coerceIn(0f, maxY)

                        v.animate()
                            .x(newX)
                            .y(newY)
                            .setDuration(0)
                            .start()
                    }

                    MotionEvent.ACTION_UP -> {
                        val buttonX = v.x
                        val buttonY = v.y

                        val closestX =
                            if (buttonX < screenWidth / 2) 0f else (screenWidth - v.width).toFloat()

                        v.animate()
                            .x(closestX)
                            .y(buttonY)
                            .setDuration(300)
                            .start()

                        startOpacityTimer()
                    }
                }
                true
            }

        }
    }

    private fun startOpacityTimer() {
        fadeJob?.cancel()
        fadeJob = lifecycleScope.launch {
            delay(1000)
            binding.draggableScreenCaptureLayout.root
                .animate()
                .alpha(0.5f)
                .setDuration(300)
                .start()
        }
    }

    private fun resetOpacity() {
        fadeJob?.cancel()
        binding.draggableScreenCaptureLayout.root
            .animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun animateStartScreenRecordingButtonIcon(@DrawableRes icon: Int) {
        binding.draggableScreenCaptureLayout.startStopRecordingBtnIconIv.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(150)
            .withEndAction {
                binding.draggableScreenCaptureLayout.startStopRecordingBtnIconIv.setImageResource(
                    icon
                )
                binding.draggableScreenCaptureLayout.startStopRecordingBtnIconIv.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun startPulsingAnimationForRecordIndicator() {
        binding.draggableScreenCaptureLayout.recordingIndicatorIv.visibility = View.VISIBLE
        val scaleX = ObjectAnimator.ofFloat(
            binding.draggableScreenCaptureLayout.recordingIndicatorIv,
            "scaleX",
            0.5f, 0.8f
        )

        val scaleY = ObjectAnimator.ofFloat(
            binding.draggableScreenCaptureLayout.recordingIndicatorIv,
            "scaleY",
            0.5f, 0.8f
        )

        scaleX.duration = 800
        scaleY.duration = 800

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleX.repeatMode = ValueAnimator.REVERSE

        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatMode = ValueAnimator.REVERSE

        scaleX.start()
        scaleY.start()

        binding.draggableScreenCaptureLayout.recordingIndicatorIv.tag = 1
    }

    private fun stopPulsingAnimationForRecordIndicator() {
        binding.draggableScreenCaptureLayout.recordingIndicatorIv.visibility = View.GONE
        val animator =
            binding.draggableScreenCaptureLayout.recordingIndicatorIv.getTag(1) as? ObjectAnimator
        animator?.cancel()
    }

    private fun getOutputPath(): String {
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

        return outputPath
    }

    private fun showPreviewDialog() {
        val bundle = Bundle().apply {
            putString(ScreenRecordingService.OUTPUT_PATH, recordOutputPath)
        }
        navController.navigate(R.id.action_global_previewDialog, bundle)
    }
}
