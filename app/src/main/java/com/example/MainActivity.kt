package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Document
import com.example.data.ScannedPage
import com.example.ui.ScannerViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

import org.opencv.android.OpenCVLoader
import android.util.Log
import android.view.KeyEvent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaSession
import android.os.Build
import androidx.lifecycle.ViewModelProvider

enum class AppScreen {
    DOCUMENT_LIST,
    DOCUMENT_PREVIEW,
    IMAGE_WORKSPACE,
    OCR_ACCESS,
    CAMERA_STREAM,
    SCAN_SETTINGS
}

class MainActivity : ComponentActivity() {
    private val viewModel: ScannerViewModel by lazy {
        ViewModelProvider(this)[ScannerViewModel::class.java]
    }

    private var mediaSession: MediaSession? = null

    // Broadcast receiver to intercept the Android system ACTION_CAMERA_BUTTON event
    // Calling abortBroadcast() prevents Android OS from opening the default phone camera app!
    private val cameraButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CAMERA_BUTTON) {
                try {
                    abortBroadcast()
                } catch (e: Exception) {
                    Log.d("MainActivity", "abortBroadcast notice: ${e.message}")
                }
                handleShutterTrigger()
            }
        }
    }

    private fun isShutterKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_FOCUS,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_1,
            KeyEvent.KEYCODE_BUTTON_2,
            KeyEvent.KEYCODE_ZOOM_IN,
            KeyEvent.KEYCODE_ZOOM_OUT -> true
            else -> false
        }
    }

    private fun handleShutterTrigger() {
        if (!viewModel.bluetoothShutterEnabled.value) return
        Log.d("MainActivity", "handleShutterTrigger: isCameraActive=${viewModel.isCameraActive.value}")
        if (viewModel.isCameraActive.value) {
            // Already in camera scanner: take capture directly
            viewModel.triggerRemoteShutter()
        } else {
            // Outside camera: open in-app camera scanner directly
            viewModel.requestNavigateToCamera()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.bluetoothShutterEnabled.value && isShutterKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                handleShutterTrigger()
            }
            return true // Consume both ACTION_DOWN and ACTION_UP completely
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.bluetoothShutterEnabled.value && isShutterKey(keyCode)) {
            if (event?.repeatCount == 0) {
                handleShutterTrigger()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.bluetoothShutterEnabled.value && isShutterKey(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent?): Boolean {
        if (viewModel.bluetoothShutterEnabled.value && isShutterKey(keyCode)) {
            handleShutterTrigger()
            return true
        }
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_CAMERA_BUTTON) {
            handleShutterTrigger()
        }
    }

    override fun onStart() {
        super.onStart()
        // Register receiver with high priority so Android OS camera app is NOT invoked
        try {
            val filter = IntentFilter(Intent.ACTION_CAMERA_BUTTON).apply {
                priority = 10000
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(cameraButtonReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(cameraButtonReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Register camera button receiver failed: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(cameraButtonReceiver)
        } catch (e: Exception) {
            // Ignored if already unregistered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (e: Exception) {
            // Ignored
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            System.loadLibrary("opencv_java4")
            Log.d("OpenCV", "OpenCV native library loaded successfully")
        } catch (e: Throwable) {
            try {
                if (OpenCVLoader.initDebug()) {
                    Log.d("OpenCV", "OpenCV loaded successfully via initDebug")
                }
            } catch (t: Throwable) {
                Log.w("OpenCV", "OpenCV initialization notice: ${t.message}")
            }
        }
        
        // Edge to edge immersive design layouts
        enableEdgeToEdge()

        try {
            mediaSession = MediaSession(this, "DocScannerRemote").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            handleShutterTrigger()
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "MediaSession init: ${e.message}")
        }
        
        setContent {
            val darkMode by viewModel.darkModeEnabled.collectAsState()
            
            MyApplicationTheme(darkTheme = darkMode) {
                val snackbarHostState = remember { SnackbarHostState() }
                
                // Permission handler
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                // Navigation Stack State routing
                var currentScreen by remember { mutableStateOf(AppScreen.DOCUMENT_LIST) }
                var activePageForEdit by remember { mutableStateOf<ScannedPage?>(null) }
                var activePageForOcr by remember { mutableStateOf<ScannedPage?>(null) }
                
                // Listen for remote shutter trigger when outside camera to open camera directly
                LaunchedEffect(Unit) {
                    viewModel.navigateToCameraEvent.collect {
                        currentScreen = AppScreen.CAMERA_STREAM
                    }
                }
                
                // Handle backwards routing
                val navigateBack = {
                    when (currentScreen) {
                        AppScreen.DOCUMENT_PREVIEW -> currentScreen = AppScreen.DOCUMENT_LIST
                        AppScreen.IMAGE_WORKSPACE -> currentScreen = AppScreen.DOCUMENT_PREVIEW
                        AppScreen.OCR_ACCESS -> currentScreen = AppScreen.DOCUMENT_PREVIEW
                        AppScreen.CAMERA_STREAM -> currentScreen = AppScreen.DOCUMENT_LIST
                        AppScreen.SCAN_SETTINGS -> currentScreen = AppScreen.DOCUMENT_LIST
                        else -> {}
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    
                    // Cross-fade screen transitions anim
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "Screen Transitions"
                    ) { screen ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (screen) {
                                AppScreen.DOCUMENT_LIST -> {
                                    DocumentListScreen(
                                        viewModel = viewModel,
                                        onNavigateToPreview = { doc ->
                                            viewModel.selectDocument(doc)
                                            currentScreen = AppScreen.DOCUMENT_PREVIEW
                                        },
                                        onNavigateToCamera = {
                                            currentScreen = AppScreen.CAMERA_STREAM
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = AppScreen.SCAN_SETTINGS
                                        }
                                    )
                                }
                                AppScreen.DOCUMENT_PREVIEW -> {
                                    DocumentPreviewScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = navigateBack,
                                        onNavigateToWorkspace = { page ->
                                            activePageForEdit = page
                                            currentScreen = AppScreen.IMAGE_WORKSPACE
                                        },
                                        onNavigateToOcrPanel = { page ->
                                            activePageForOcr = page
                                            currentScreen = AppScreen.OCR_ACCESS
                                        }
                                    )
                                }
                                AppScreen.IMAGE_WORKSPACE -> {
                                    val page = activePageForEdit
                                    if (page != null) {
                                        ImageWorkspaceScreen(
                                            page = page,
                                            viewModel = viewModel,
                                            onNavigateBack = navigateBack
                                        )
                                    } else {
                                        currentScreen = AppScreen.DOCUMENT_PREVIEW
                                    }
                                }
                                AppScreen.OCR_ACCESS -> {
                                    val page = activePageForOcr
                                    if (page != null) {
                                        OcrAccessPanel(
                                            page = page,
                                            viewModel = viewModel,
                                            onNavigateBack = navigateBack
                                        )
                                    } else {
                                        currentScreen = AppScreen.DOCUMENT_PREVIEW
                                    }
                                }
                                AppScreen.CAMERA_STREAM -> {
                                    CameraScreen(
                                        viewModel = viewModel,
                                        onFinishedCapturing = { pages, config ->
                                            viewModel.processBatchScannedPages(pages, config) { doc, pdfFile ->
                                                viewModel.selectDocument(doc)
                                                currentScreen = AppScreen.DOCUMENT_PREVIEW
                                            }
                                        },
                                        onCancel = {
                                            currentScreen = AppScreen.DOCUMENT_LIST
                                        },
                                        onOpenSettings = {
                                            currentScreen = AppScreen.SCAN_SETTINGS
                                        }
                                    )
                                }
                                AppScreen.SCAN_SETTINGS -> {
                                    ScanSettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = navigateBack,
                                        onNavigateToCamera = {
                                            currentScreen = AppScreen.CAMERA_STREAM
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
