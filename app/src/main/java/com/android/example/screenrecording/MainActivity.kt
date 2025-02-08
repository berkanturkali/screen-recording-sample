package com.android.example.screenrecording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.android.example.screenrecording.databinding.ActivityMainBinding
import com.android.example.screenrecording.service.ScreenRecordingService
import com.android.example.screenrecording.viewmodel.MainActivityViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var navController: NavController

    private val viewModel by viewModels<MainActivityViewModel>()

    private var dX = 0f
    private var dY = 0f

    private var screenWidth = 0
    private var screenHeight = 0

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setClickListeners() {
        binding.apply {
            backIv.setOnClickListener {
                navController.navigateUp()
            }
            moveNextBtn.setOnClickListener {
                navController.navigate(viewModel.navigationActionId)
            }

            draggableScreenCaptureLayout.root.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val maxX = (screenWidth - v.width).toFloat().coerceAtLeast(0f)
                        val maxY = (screenHeight - v.height).toFloat().coerceAtLeast(0f)

                        val newX = (event.rawX + dX).coerceIn(0f, maxX)
                        val newY = (event.rawY + dY).coerceIn(0f, maxY)

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
                    }
                }
                true
            }

        }
    }

}
