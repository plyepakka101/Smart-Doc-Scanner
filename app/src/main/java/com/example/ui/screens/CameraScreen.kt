package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.RectF
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.components.rememberGalleryPickerLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

enum class ScanMode {
    SINGLE,
    MULTI_PAGE,
    BOOK_2PAGE
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: com.example.ui.ScannerViewModel? = null,
    initialMode: ScanMode = ScanMode.BOOK_2PAGE,
    onFinishedCapturing: (List<Pair<Uri, List<Offset>?>>, com.example.ui.BatchScanConfig) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    val capturedPages = remember { mutableStateListOf<Pair<Uri, List<Offset>?>>() }
    val defaultMode = remember {
        when (viewModel?.preScanScanMode?.value) {
            "SINGLE" -> ScanMode.SINGLE
            "MULTI_PAGE" -> ScanMode.MULTI_PAGE
            "BOOK_2PAGE" -> ScanMode.BOOK_2PAGE
            else -> initialMode
        }
    }
    var scanMode by remember { mutableStateOf(defaultMode) }
    var autoOrientationEnabled by remember { mutableStateOf(viewModel?.preScanAutoOrientation?.value ?: true) }
    var autoCaptureEnabled by remember { mutableStateOf(viewModel?.preScanAutoCapture?.value ?: false) }
    var orientationNotification by remember { mutableStateOf<String?>(null) }
    var flashFeedback by remember { mutableStateOf(false) }

    // Camera Distance / Zoom state (defaults to closer distance for books and documents)
    var currentZoomRatio by remember { mutableFloatStateOf(viewModel?.preScanCameraZoom?.value ?: 1.25f) }
    var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
    var maxZoomRatio by remember { mutableFloatStateOf(6.0f) }
    var zoomNotification by remember { mutableStateOf<String?>(null) }

    // Direct in-app Bluetooth shutter trigger callback
    var captureActionTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Immediate lifecycle active flag for remote shutter
    DisposableEffect(Unit) {
        viewModel?.setCameraActive(true)
        onDispose {
            viewModel?.setCameraActive(false)
        }
    }

    // Top-level collector for Bluetooth Remote Shutter events: triggers in-app scan directly
    LaunchedEffect(Unit) {
        viewModel?.remoteShutterEvent?.collect {
            val trigger = captureActionTrigger
            if (trigger != null) {
                trigger.invoke()
                zoomNotification = "📸 ถ่ายภาพเรียบร้อย"
            } else {
                Log.d("CameraScreen", "Remote shutter received before captureActionTrigger ready")
            }
        }
    }

    // Auto-dismiss transient notification after 1.8s so it never stays stuck blocking the document
    LaunchedEffect(zoomNotification) {
        if (zoomNotification != null) {
            kotlinx.coroutines.delay(1800)
            zoomNotification = null
        }
    }

    // Book Aspect Ratio for BOOK_2PAGE mode (Standard A4/A5 is 1.414 : 1, Pocket Book is 1.333 : 1)
    val persistedBookRatio by (viewModel?.bookAspectRatio?.collectAsState() ?: remember { mutableFloatStateOf(1.414f) })
    var currentBookRatio by remember(persistedBookRatio) { mutableFloatStateOf(persistedBookRatio) }
    var calculatedGuideCorners by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Gallery & External Image Picker Launcher
    val galleryPicker = rememberGalleryPickerLauncher { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                zoomNotification = "กำลังนำเข้ารูปภาพ ${uris.size} รูป..."
                withContext(Dispatchers.IO) {
                    for ((idx, uri) in uris.withIndex()) {
                        val bitmap = com.example.util.ImageProcessor.decodeBitmapWithExif(context, uri) ?: continue
                        val cacheFile = File(context.cacheDir, "import_${System.currentTimeMillis()}_$idx.jpg")
                        java.io.FileOutputStream(cacheFile).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        bitmap.recycle()
                        val localUri = Uri.fromFile(cacheFile)

                        if (scanMode == ScanMode.BOOK_2PAGE) {
                            val isDewarp = viewModel?.autoDewarpEnabled?.value ?: true
                            val isFinger = viewModel?.autoFingerRemovalEnabled?.value ?: true
                            val splitUris = com.example.util.ImageProcessor.splitBookPage(
                                context = context,
                                originalUri = localUri,
                                applyDeWarp = isDewarp,
                                applyFingerRemoval = isFinger,
                                bookAspectRatio = currentBookRatio
                            )
                            if (splitUris.size == 2) {
                                withContext(Dispatchers.Main) {
                                    capturedPages.add(Pair(splitUris[0], null))
                                    capturedPages.add(Pair(splitUris[1], null))
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    capturedPages.add(Pair(localUri, null))
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                capturedPages.add(Pair(localUri, null))
                            }
                        }
                    }
                }
                zoomNotification = "🖼️ นำเข้ารูปภาพ ${uris.size} รูปเรียบร้อย"
                android.widget.Toast.makeText(context, "นำเข้ารูปภาพแล้ว (${capturedPages.size} หน้า)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showBatchFinishDialog by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var quickPreviewPageIndex by remember { mutableStateOf<Int?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Focus state
    var focusRingPosition by remember { mutableStateOf<Offset?>(null) }
    var focusRingVisible by remember { mutableStateOf(false) }
    var lastFocusedCentroid by remember { mutableStateOf<Offset?>(null) }
    var pageJustCaptured by remember { mutableStateOf(false) }
    var autoCaptureProgress by remember { mutableStateOf(0f) }

    // Dynamic Orientation Sensor Listener: switches to SINGLE in portrait and BOOK_2PAGE in landscape
    DisposableEffect(autoOrientationEnabled) {
        if (!autoOrientationEnabled) {
            onDispose { }
        } else {
            val orientationListener = object : android.view.OrientationEventListener(context) {
                override fun onOrientationChanged(degrees: Int) {
                    if (degrees == ORIENTATION_UNKNOWN) return
                    // Landscape tilt: 60°..120° (landscape left) or 240°..300° (landscape right)
                    val isLandscape = (degrees in 60..120) || (degrees in 240..300)
                    // Portrait tilt: 0°..30° or 330°..360° or 150°..210°
                    val isPortrait = (degrees in 0..30) || (degrees in 330..360) || (degrees in 150..210)

                    if (isLandscape && scanMode != ScanMode.BOOK_2PAGE) {
                        scanMode = ScanMode.BOOK_2PAGE
                        orientationNotification = "📖 ตรวจพบแนวนอน: สลับเป็นโหมดสแกนหนังสือ 2 หน้า"
                    } else if (isPortrait && scanMode == ScanMode.BOOK_2PAGE) {
                        scanMode = ScanMode.SINGLE
                        orientationNotification = "📱 ตรวจพบแนวตั้ง: สลับเป็นโหมดสแกนหน้าเดียว"
                    }
                }
            }
            if (orientationListener.canDetectOrientation()) {
                orientationListener.enable()
            }
            onDispose {
                orientationListener.disable()
            }
        }
    }

    LaunchedEffect(orientationNotification) {
        if (orientationNotification != null) {
            kotlinx.coroutines.delay(2200)
            orientationNotification = null
        }
    }

    LaunchedEffect(zoomNotification) {
        if (zoomNotification != null) {
            kotlinx.coroutines.delay(1800)
            zoomNotification = null
        }
    }

    // When switching to BOOK_2PAGE, adapt camera zoom closer to comfortably frame an open book
    LaunchedEffect(scanMode) {
        if (scanMode == ScanMode.BOOK_2PAGE && currentZoomRatio < 1.15f) {
            currentZoomRatio = 1.25f
            zoomNotification = "📖 ปรับสัดส่วนและระยะกล้องสำหรับหนังสือ 2 หน้า"
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        val previewView = remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        val imageCapture = remember { ImageCapture.Builder().build() }
        var detectedCorners by remember { mutableStateOf<List<Offset>?>(null) }
        var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }

        LaunchedEffect(currentZoomRatio, cameraControlRef) {
            val control = cameraControlRef ?: return@LaunchedEffect
            try {
                control.setZoomRatio(currentZoomRatio.coerceIn(minZoomRatio, maxZoomRatio))
            } catch (e: Exception) {
                Log.w("CameraScreen", "Error setting zoom ratio: ${e.message}")
            }
        }

        LaunchedEffect(isTorchOn, cameraControlRef) {
            val control = cameraControlRef ?: return@LaunchedEffect
            try {
                control.enableTorch(isTorchOn)
            } catch (e: Exception) {
                Log.w("CameraScreen", "Torch error: ${e.message}")
            }
        }

        fun triggerFocusAt(screenX: Float, screenY: Float) {
            val control = cameraControlRef ?: return
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(screenX, screenY)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            control.startFocusAndMetering(action)
            focusRingPosition = Offset(screenX, screenY)
            focusRingVisible = true
        }
        
        LaunchedEffect(previewView) {
            val cameraProvider = context.getCameraProvider()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), DocumentBoundaryAnalyzer({ scanMode }) { corners ->
                        detectedCorners = corners
                    })
                }
            
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )
                cameraControlRef = camera.cameraControl
                
                // Observe camera hardware zoom capabilities
                camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                    if (zoomState != null) {
                        minZoomRatio = zoomState.minZoomRatio
                        maxZoomRatio = zoomState.maxZoomRatio.coerceAtMost(6.0f)
                    }
                }
                
                // Set initial zoom closer to book
                camera.cameraControl.setZoomRatio(currentZoomRatio.coerceIn(minZoomRatio, maxZoomRatio))
            } catch (exc: Exception) {
                Log.e("CameraScreen", "Use case binding failed", exc)
            }
        }

        fun executeCaptureAction() {
            flashFeedback = true
            val cornersToPass = detectedCorners ?: calculatedGuideCorners
            takePhoto(
                context = context,
                imageCapture = imageCapture,
                corners = cornersToPass,
                onImageCaptured = { uri, corners ->
                    pageJustCaptured = true
                    autoCaptureProgress = 0f
                    if (scanMode == ScanMode.BOOK_2PAGE) {
                        val cropPointFs = (corners ?: calculatedGuideCorners).let { list ->
                            if (list.size == 4) list.map { PointF(it.x, it.y) } else null
                        }
                        val isDewarp = viewModel?.autoDewarpEnabled?.value ?: true
                        val isFinger = viewModel?.autoFingerRemovalEnabled?.value ?: true
                        val splitUris = com.example.util.ImageProcessor.splitBookPage(
                            context = context,
                            originalUri = uri,
                            cropPoints = cropPointFs,
                            applyDeWarp = isDewarp,
                            applyFingerRemoval = isFinger,
                            bookAspectRatio = currentBookRatio
                        )
                        if (splitUris.size == 2) {
                            capturedPages.add(Pair(splitUris[0], null))
                            capturedPages.add(Pair(splitUris[1], null))
                            android.widget.Toast.makeText(context, "สแกนแยกหน้าซ้าย-ขวาอัตโนมัติ (${capturedPages.size} หน้า)", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            capturedPages.add(Pair(uri, corners))
                            android.widget.Toast.makeText(context, "บันทึกหน้าที่ ${capturedPages.size} แล้ว", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else if (scanMode == ScanMode.SINGLE) {
                        val newPage = Pair(uri, corners)
                        onFinishedCapturing(listOf(newPage), com.example.ui.BatchScanConfig(autoGeneratePdf = true))
                    } else {
                        val newPage = Pair(uri, corners)
                        capturedPages.add(newPage)
                        android.widget.Toast.makeText(context, "บันทึกหน้าที่ ${capturedPages.size} แล้ว (แตะ 'เสร็จสิ้น' เมื่อถ่ายครบ)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { Log.e("CameraScreen", "Capture failed: ", it) }
            )
        }

        // Auto Focus & Auto Capture on New Page Detection Loop
        LaunchedEffect(detectedCorners, autoCaptureEnabled, pageJustCaptured) {
            val corners = detectedCorners
            if (corners != null && corners.size == 4) {
                val p0 = corners[0]
                val p1 = corners[1]
                val p2 = corners[2]
                val p3 = corners[3]
                val currentCentroid = Offset(
                    (p0.x + p1.x + p2.x + p3.x) / 4f,
                    (p0.y + p1.y + p2.y + p3.y) / 4f
                )

                val prevCentroid = lastFocusedCentroid
                val distance = if (prevCentroid != null) {
                    val dx = currentCentroid.x - prevCentroid.x
                    val dy = currentCentroid.y - prevCentroid.y
                    kotlin.math.sqrt(dx * dx + dy * dy)
                } else 1.0f

                // If page changed significantly (> 0.12) or fresh document detected
                if (distance > 0.12f || prevCentroid == null) {
                    lastFocusedCentroid = currentCentroid
                    pageJustCaptured = false
                    autoCaptureProgress = 0f

                    // Trigger Auto Focus at new page center
                    val viewW = previewView.width.takeIf { it > 0 } ?: 1080
                    val viewH = previewView.height.takeIf { it > 0 } ?: 1920
                    val focusPx = currentCentroid.x * viewW
                    val focusPy = currentCentroid.y * viewH
                    triggerFocusAt(focusPx, focusPy)
                }

                // If auto capture enabled and page not already captured
                if (autoCaptureEnabled && !pageJustCaptured) {
                    // Count up progress over ~1.1 seconds of stable framing
                    val steps = 11
                    for (i in 1..steps) {
                        kotlinx.coroutines.delay(100)
                        autoCaptureProgress = i / steps.toFloat()
                    }
                    // Execute auto capture!
                    executeCaptureAction()
                }
            } else {
                // If document is no longer in frame (page turned/moved away)
                autoCaptureProgress = 0f
                if (pageJustCaptured) {
                    // Reset ready for next page
                    pageJustCaptured = false
                    lastFocusedCentroid = null
                }
            }
        }

        // Wire up remote shutter capture trigger callback
        captureActionTrigger = { executeCaptureAction() }

        // Fade out focus ring animation
        LaunchedEffect(focusRingVisible) {
            if (focusRingVisible) {
                kotlinx.coroutines.delay(1200)
                focusRingVisible = false
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val nextZoom = if (currentZoomRatio >= 1.25f) 1.0f else 1.25f
                            currentZoomRatio = nextZoom
                            zoomNotification = "🔍 ระยะกล้อง: ${String.format(Locale.US, "%.2f", nextZoom)}x"
                            triggerFocusAt(tapOffset.x, tapOffset.y)
                        },
                        onTap = { tapOffset ->
                            triggerFocusAt(tapOffset.x, tapOffset.y)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            val newZoom = (currentZoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                            if (kotlin.math.abs(newZoom - currentZoomRatio) > 0.015f) {
                                currentZoomRatio = newZoom
                                zoomNotification = "🔍 ${String.format(Locale.US, "%.1f", newZoom)}x"
                            }
                        }
                    }
                }
        ) {
            val density = LocalDensity.current
            val screenW = with(density) { maxWidth.toPx() }
            val screenH = with(density) { maxHeight.toPx() }
            val isPortrait = screenH > screenW

            // Mathematically precise book / document framing matching physical book proportions
            val defaultCorners = remember(scanMode, currentBookRatio, screenW, screenH) {
                if (scanMode == ScanMode.BOOK_2PAGE) {
                    val targetRatio = if (currentBookRatio > 0.5f) currentBookRatio else 1.414f
                    if (isPortrait) {
                        // Portrait mode: An open book spread lies horizontally across phone view
                        // Width spans 90% of screen width with comfortable padding
                        val bookW = screenW * 0.90f
                        // Height is strictly determined by book aspect ratio (Spread Width / Height = targetRatio)
                        val bookH = (bookW / targetRatio).coerceAtMost(screenH * 0.55f)
                        val centerY = screenH * 0.44f // Golden scanning ergonomic center
                        val leftPx = (screenW - bookW) / 2f
                        val rightPx = leftPx + bookW
                        val topPx = (centerY - bookH / 2f).coerceAtLeast(screenH * 0.12f)
                        val bottomPx = topPx + bookH
                        listOf(
                            Offset(leftPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, bottomPx / screenH),
                            Offset(leftPx / screenW, bottomPx / screenH)
                        )
                    } else {
                        // Landscape mode: Book spread fits landscape screen
                        val bookH = screenH * 0.80f
                        var bookW = bookH * targetRatio
                        if (bookW > screenW * 0.92f) {
                            bookW = screenW * 0.92f
                            val adjH = bookW / targetRatio
                            val leftPx = (screenW - bookW) / 2f
                            val rightPx = leftPx + bookW
                            val topPx = (screenH - adjH) / 2f
                            val bottomPx = topPx + adjH
                            listOf(
                                Offset(leftPx / screenW, topPx / screenH),
                                Offset(rightPx / screenW, topPx / screenH),
                                Offset(rightPx / screenW, bottomPx / screenH),
                                Offset(leftPx / screenW, bottomPx / screenH)
                            )
                        } else {
                            val leftPx = (screenW - bookW) / 2f
                            val rightPx = leftPx + bookW
                            val topPx = (screenH - bookH) / 2f
                            val bottomPx = topPx + bookH
                            listOf(
                                Offset(leftPx / screenW, topPx / screenH),
                                Offset(rightPx / screenW, topPx / screenH),
                                Offset(rightPx / screenW, bottomPx / screenH),
                                Offset(leftPx / screenW, bottomPx / screenH)
                            )
                        }
                    }
                } else {
                    // Single Page Document (standard A4 portrait ratio 1 : 1.414)
                    if (isPortrait) {
                        val docW = screenW * 0.84f
                        val docH = (docW * 1.414f).coerceAtMost(screenH * 0.68f)
                        val centerY = screenH * 0.44f
                        val leftPx = (screenW - docW) / 2f
                        val rightPx = leftPx + docW
                        val topPx = (centerY - docH / 2f).coerceAtLeast(screenH * 0.12f)
                        val bottomPx = topPx + docH
                        listOf(
                            Offset(leftPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, bottomPx / screenH),
                            Offset(leftPx / screenW, bottomPx / screenH)
                        )
                    } else {
                        val docH = screenH * 0.82f
                        val docW = (docH / 1.414f).coerceAtMost(screenW * 0.70f)
                        val leftPx = (screenW - docW) / 2f
                        val rightPx = leftPx + docW
                        val topPx = (screenH - docH) / 2f
                        val bottomPx = topPx + docH
                        listOf(
                            Offset(leftPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, topPx / screenH),
                            Offset(rightPx / screenW, bottomPx / screenH),
                            Offset(leftPx / screenW, bottomPx / screenH)
                        )
                    }
                }
            }

            LaunchedEffect(defaultCorners) {
                calculatedGuideCorners = defaultCorners
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Capture Flash Animation Feedback
            if (flashFeedback) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.7f))
                )
                LaunchedEffect(flashFeedback) {
                    kotlinx.coroutines.delay(120)
                    flashFeedback = false
                }
            }

            // Focus Ring Indicator
            if (focusRingVisible && focusRingPosition != null) {
                val pos = focusRingPosition!!
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.Green,
                        radius = 45f,
                        center = pos,
                        style = Stroke(width = 4f)
                    )
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.3f),
                        radius = 18f,
                        center = pos
                    )
                    // 4 corner crosshairs
                    val ch = 15f
                    drawLine(Color.Green, Offset(pos.x - 55f, pos.y), Offset(pos.x - 55f + ch, pos.y), 3f)
                    drawLine(Color.Green, Offset(pos.x + 55f - ch, pos.y), Offset(pos.x + 55f, pos.y), 3f)
                    drawLine(Color.Green, Offset(pos.x, pos.y - 55f), Offset(pos.x, pos.y - 55f + ch), 3f)
                    drawLine(Color.Green, Offset(pos.x, pos.y + 55f - ch), Offset(pos.x, pos.y + 55f), 3f)
                }
            }

            // Computer Vision Overlay - smoothly follows detected book bounds or standard ratio guide
            val targetP0 = detectedCorners?.getOrNull(0) ?: defaultCorners[0]
            val targetP1 = detectedCorners?.getOrNull(1) ?: defaultCorners[1]
            val targetP2 = detectedCorners?.getOrNull(2) ?: defaultCorners[2]
            val targetP3 = detectedCorners?.getOrNull(3) ?: defaultCorners[3]

            val p0x by animateFloatAsState(targetValue = targetP0.x, animationSpec = tween(300))
            val p0y by animateFloatAsState(targetValue = targetP0.y, animationSpec = tween(300))
            val p1x by animateFloatAsState(targetValue = targetP1.x, animationSpec = tween(300))
            val p1y by animateFloatAsState(targetValue = targetP1.y, animationSpec = tween(300))
            val p2x by animateFloatAsState(targetValue = targetP2.x, animationSpec = tween(300))
            val p2y by animateFloatAsState(targetValue = targetP2.y, animationSpec = tween(300))
            val p3x by animateFloatAsState(targetValue = targetP3.x, animationSpec = tween(300))
            val p3y by animateFloatAsState(targetValue = targetP3.y, animationSpec = tween(300))

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val pt0 = Offset(p0x * canvasWidth, p0y * canvasHeight)
                val pt1 = Offset(p1x * canvasWidth, p1y * canvasHeight)
                val pt2 = Offset(p2x * canvasWidth, p2y * canvasHeight)
                val pt3 = Offset(p3x * canvasWidth, p3y * canvasHeight)

                val path = Path().apply {
                    moveTo(pt0.x, pt0.y)
                    lineTo(pt1.x, pt1.y)
                    lineTo(pt2.x, pt2.y)
                    lineTo(pt3.x, pt3.y)
                    close()
                }

                // Draw overlay mask (lighter mask so the book is always clearly visible)
                val outerPath = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasWidth, canvasHeight))
                    op(this, path, androidx.compose.ui.graphics.PathOperation.Difference)
                }
                drawPath(
                    path = outerPath,
                    color = Color.Black.copy(alpha = 0.25f)
                )

                // Draw detected boundary box (vFlat signature emerald mint green)
                drawPath(
                    path = path,
                    color = Color(0xFF00E599).copy(alpha = 0.90f),
                    style = Stroke(width = 5f)
                )

                // Book 2-Page middle spine guideline if in BOOK_2PAGE mode
                if (scanMode == ScanMode.BOOK_2PAGE) {
                    val midTopX = (pt0.x + pt1.x) / 2f
                    val midTopY = (pt0.y + pt1.y) / 2f
                    val midBottomX = (pt3.x + pt2.x) / 2f
                    val midBottomY = (pt3.y + pt2.y) / 2f

                    // Soft spine guide glow (vFlat mint)
                    drawLine(
                        color = Color(0xFF00E599).copy(alpha = 0.40f),
                        start = Offset(midTopX, midTopY),
                        end = Offset(midBottomX, midBottomY),
                        strokeWidth = 12f
                    )
                    // Spine center line
                    drawLine(
                        color = Color(0xFF00E599),
                        start = Offset(midTopX, midTopY),
                        end = Offset(midBottomX, midBottomY),
                        strokeWidth = 3.5f
                    )
                }
                
                // Draw corner indicators for alignment assist
                val cornerLength = 40f
                val cornerPath = Path().apply {
                    // Top Left (pt0)
                    moveTo(pt0.x, pt0.y + cornerLength)
                    lineTo(pt0.x, pt0.y)
                    lineTo(pt0.x + cornerLength, pt0.y)
                    // Top Right (pt1)
                    moveTo(pt1.x - cornerLength, pt1.y)
                    lineTo(pt1.x, pt1.y)
                    lineTo(pt1.x, pt1.y + cornerLength)
                    // Bottom Right (pt2)
                    moveTo(pt2.x, pt2.y - cornerLength)
                    lineTo(pt2.x, pt2.y)
                    lineTo(pt2.x - cornerLength, pt2.y)
                    // Bottom Left (pt3)
                    moveTo(pt3.x + cornerLength, pt3.y)
                    lineTo(pt3.x, pt3.y)
                    lineTo(pt3.x, pt3.y - cornerLength)
                }
                drawPath(
                    path = cornerPath,
                    color = Color.White,
                    style = Stroke(width = 8f)
                )
            }

            // Minimal, Unobtrusive Top Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close / Exit Button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Page Count Badge in Top Center (Only when pages exist)
                    if (capturedPages.isNotEmpty()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.60f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E599).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "สแกนแล้ว ${capturedPages.size} หน้า",
                                color = Color(0xFF00E599),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Action Icons Row: Flash, Bluetooth Status, Settings
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Flash / Torch Toggle
                        IconButton(
                            onClick = { isTorchOn = !isTorchOn },
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (isTorchOn) Color(0xFF00C187) else Color.Black.copy(alpha = 0.45f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "แฟลช",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 2. Bluetooth Shutter Active Indicator (Minimal icon, no bulky banners)
                        val isBtEnabled by (viewModel?.bluetoothShutterEnabled?.collectAsState() ?: remember { mutableStateOf(true) })
                        if (isBtEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothConnected,
                                    contentDescription = "รีโมทชัตเตอร์บลูทูธพร้อมใช้งาน",
                                    tint = Color(0xFF00E599),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 3. Pre-Scan Settings Gear
                        if (onOpenSettings != null) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "ตั้งค่า",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }

                // Transient Notification Toast (Auto-fades after 1.8s, zero persistent blocking)
                AnimatedVisibility(
                    visible = zoomNotification != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 }),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E599).copy(alpha = 0.4f)),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = zoomNotification ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Clean Bottom Controls Area (Spacious & Respecting Insets)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp, top = 12.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selector Bar (1 หน้า | 2 หน้า | หลายหน้า - vFlat Style)
                Row(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Single Page
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (scanMode == ScanMode.SINGLE) Color(0xFF00C187) else Color.Transparent)
                            .clickable { scanMode = ScanMode.SINGLE }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "1 หน้า",
                            color = if (scanMode == ScanMode.SINGLE) Color.White else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = if (scanMode == ScanMode.SINGLE) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    // 2. Book 2-Page (vFlat's signature feature)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (scanMode == ScanMode.BOOK_2PAGE) Color(0xFF00C187) else Color.Transparent)
                            .clickable { scanMode = ScanMode.BOOK_2PAGE }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "2 หน้า (หนังสือ)",
                                color = if (scanMode == ScanMode.BOOK_2PAGE) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = if (scanMode == ScanMode.BOOK_2PAGE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // 3. Multi-Page Batch
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (scanMode == ScanMode.MULTI_PAGE) Color(0xFF00C187) else Color.Transparent)
                            .clickable { scanMode = ScanMode.MULTI_PAGE }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "หลายหน้า",
                            color = if (scanMode == ScanMode.MULTI_PAGE) Color.White else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = if (scanMode == ScanMode.MULTI_PAGE) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Action Controls Row (Gallery/Thumbnail - Iconic Shutter - Done/Finish)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Control: Gallery import or Latest Captured Page preview thumbnail
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedPages.isNotEmpty()) {
                            // Thumbnail with page badge
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(2.dp, Color(0xFF00E599), RoundedCornerShape(10.dp))
                                    .clickable {
                                        quickPreviewPageIndex = capturedPages.lastIndex
                                    }
                            ) {
                                AsyncImage(
                                    model = capturedPages.last().first,
                                    contentDescription = "หน้าล่าสุดที่ถ่าย",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Counter Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xFF00C187), RoundedCornerShape(bottomStart = 6.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${capturedPages.size}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { galleryPicker.launch() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "นำเข้ารูปภาพจากคลัง",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Main vFlat Shutter Button with outer ring and auto progress
                    Box(
                        modifier = Modifier.size(88.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer progress ring for auto-capture
                        if (autoCaptureEnabled && autoCaptureProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { autoCaptureProgress },
                                modifier = Modifier.size(86.dp),
                                color = Color(0xFF00E599),
                                strokeWidth = 4.dp,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        } else {
                            // Outer subtle ring
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .border(3.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            )
                        }

                        // Shutter trigger button
                        IconButton(
                            onClick = { executeCaptureAction() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White, CircleShape)
                                .border(3.dp, if (autoCaptureEnabled) Color(0xFF00C187) else Color.White, CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(
                                        if (autoCaptureEnabled) Color(0xFF00C187) else Color(0xFF00E599),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (autoCaptureEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "ถ่ายรูปอัตโนมัติ",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    // Right Control: Finish Batch or Gallery Picker (when empty)
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedPages.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (viewModel?.skipFinishConfirmDialog?.value == true) {
                                        val config = viewModel.getCurrentPreScanConfig()
                                        onFinishedCapturing(capturedPages.toList(), config)
                                    } else {
                                        showBatchFinishDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF00C187), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "เสร็จสิ้น",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            // Secondary option when no pages captured yet: Import from gallery
                            IconButton(
                                onClick = { galleryPicker.launch() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "เพิ่มรูป",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Batch Finish & Reorder Dialogs
                if (capturedPages.isNotEmpty()) {
                    if (showReorderDialog) {
                        BufferReorderDialog(
                            pages = capturedPages,
                            onDismiss = { showReorderDialog = false },
                            onConfirm = { reorderedPages ->
                                showReorderDialog = false
                                capturedPages.clear()
                                capturedPages.addAll(reorderedPages)
                            },
                            onDelete = { index ->
                                capturedPages.removeAt(index)
                                if (capturedPages.isEmpty()) {
                                    showReorderDialog = false
                                }
                            }
                        )
                    }

                    if (showBatchFinishDialog) {
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val initialTitle = "Scan_${dateFormat.format(Date())}"
                        val initialFilter = viewModel?.preScanFilterMode?.value ?: "MAGIC_COLOR"
                        val initialAutoPdf = viewModel?.preScanAutoGeneratePdf?.value ?: true
                        val initialRunOcr = viewModel?.preScanRunOcr?.value ?: false
                        val defaultFolder = viewModel?.preScanDefaultFolder?.value ?: "ทั่วไป"

                        BatchFinishOptionsDialog(
                            pagesCount = capturedPages.size,
                            previewUri = capturedPages.firstOrNull()?.first,
                            initialTitle = initialTitle,
                            initialFilter = initialFilter,
                            initialAutoPdf = initialAutoPdf,
                            initialRunOcr = initialRunOcr,
                            onDismiss = { showBatchFinishDialog = false },
                            onConfirm = { config ->
                                showBatchFinishDialog = false
                                viewModel?.setPreScanFilterMode(config.filterMode)
                                viewModel?.setPreScanAutoGeneratePdf(config.autoGeneratePdf)
                                viewModel?.setPreScanRunOcr(config.runOcr)
                                val finalConfig = config.copy(folder = defaultFolder)
                                onFinishedCapturing(capturedPages.toList(), finalConfig)
                            },
                            onOpenReorder = {
                                showBatchFinishDialog = false
                                showReorderDialog = true
                            }
                        )
                    }
                }
            }

            // Quick Live Preview Dialog when user taps on any captured page thumbnail
            if (quickPreviewPageIndex != null && quickPreviewPageIndex!! in capturedPages.indices) {
                val pageIndex = quickPreviewPageIndex!!
                val pageItem = capturedPages[pageIndex]
                CapturedPageLivePreviewDialog(
                    pageNumber = pageIndex + 1,
                    totalCount = capturedPages.size,
                    imageUri = pageItem.first,
                    onDismiss = { quickPreviewPageIndex = null },
                    onDelete = {
                        capturedPages.removeAt(pageIndex)
                        quickPreviewPageIndex = null
                    }
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required.")
        }
    }
}

private class DocumentBoundaryAnalyzer(
    private val scanModeProvider: () -> ScanMode,
    private val onBoundsDetected: (List<Offset>?) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val yBuffer = image.planes[0].buffer
            val ySize = yBuffer.remaining()
            val yArray = ByteArray(ySize)
            yBuffer.get(yArray)
            
            val width = image.width
            val height = image.height
            val rotationDegrees = image.imageInfo.rotationDegrees

            val mat = Mat(height, width, CvType.CV_8UC1)
            mat.put(0, 0, yArray)
            
            // Scale down for faster processing
            val scale = 0.25
            val resized = Mat()
            Imgproc.resize(mat, resized, org.opencv.core.Size(), scale, scale, Imgproc.INTER_AREA)
            
            Imgproc.GaussianBlur(resized, resized, org.opencv.core.Size(5.0, 5.0), 0.0)
            
            val edges = Mat()
            Imgproc.Canny(resized, edges, 75.0, 200.0)
            
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            contours.sortByDescending { Imgproc.contourArea(it) }
            
            var maxContour: MatOfPoint2f? = null
            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                val peri = Imgproc.arcLength(contour2f, true)
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
                
                if (approx.total() == 4L && Imgproc.contourArea(contour) > (resized.cols() * resized.rows() * 0.12)) {
                    val rect = Imgproc.boundingRect(contour)
                    val rWidth = rect.width.toDouble()
                    val rHeight = rect.height.toDouble()
                    val currentMode = scanModeProvider()
                    if (currentMode == ScanMode.BOOK_2PAGE) {
                        val ratio = if (rWidth > rHeight) rWidth / rHeight else rHeight / rWidth
                        if (ratio in 1.15..1.90) {
                            maxContour = approx
                            break
                        }
                    } else {
                        maxContour = approx
                        break
                    }
                }
            }
            
            if (maxContour != null) {
                val points = maxContour.toArray()
                // Map to normalized coordinates [0, 1] relative to original image
                val mappedPoints = points.map { pt ->
                    var nx = pt.x / resized.cols()
                    var ny = pt.y / resized.rows()
                    
                    // Adjust for CameraX sensor rotation vs Screen Portrait layout
                    when (rotationDegrees) {
                        90 -> {
                            val temp = nx
                            nx = 1.0 - ny
                            ny = temp
                        }
                        180 -> {
                            nx = 1.0 - nx
                            ny = 1.0 - ny
                        }
                        270 -> {
                            val temp = nx
                            nx = ny
                            ny = 1.0 - temp
                        }
                    }
                    Offset(nx.toFloat(), ny.toFloat())
                }
                
                // Sort corners to TL, TR, BR, BL order
                val tl = mappedPoints.minByOrNull { it.x + it.y } ?: Offset.Zero
                val br = mappedPoints.maxByOrNull { it.x + it.y } ?: Offset.Zero
                val tr = mappedPoints.maxByOrNull { it.x - it.y } ?: Offset.Zero
                val bl = mappedPoints.minByOrNull { it.x - it.y } ?: Offset.Zero
                
                onBoundsDetected(listOf(tl, tr, br, bl))
            } else {
                onBoundsDetected(null)
            }
            mat.release()
            resized.release()
            edges.release()
            hierarchy.release()
        } catch (e: Exception) {
            onBoundsDetected(null)
        }
        
        image.close()
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    corners: List<Offset>?,
    onImageCaptured: (Uri, List<Offset>?) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                onError(exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri, corners)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BufferReorderDialog(
    pages: List<Pair<Uri, List<Offset>?>>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<Uri, List<Offset>?>>) -> Unit,
    onDelete: (Int) -> Unit
) {
    var mutablePagesList by remember(pages) { mutableStateOf(pages.toList()) }
    var pageToMoveIndex by remember { mutableStateOf<Int?>(null) }

    if (pageToMoveIndex != null) {
        val currentIndex = pageToMoveIndex!!
        var targetPositionText by remember { mutableStateOf("${currentIndex + 1}") }

        AlertDialog(
            onDismissRequest = { pageToMoveIndex = null },
            title = { Text("ย้ายไปลำดับที่กำหนด", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("หน้าที่ ${currentIndex + 1} จากทั้งหมด ${mutablePagesList.size} หน้า", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetPositionText,
                        onValueChange = { targetPositionText = it.filter { char -> char.isDigit() } },
                        label = { Text("ใส่ลำดับเป้าหมาย (1 - ${mutablePagesList.size})") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPos = targetPositionText.toIntOrNull()
                        if (targetPos != null && targetPos in 1..mutablePagesList.size) {
                            val newIndex = targetPos - 1
                            if (newIndex != currentIndex) {
                                val newList = mutablePagesList.toMutableList()
                                val movedPage = newList.removeAt(currentIndex)
                                newList.add(newIndex, movedPage)
                                mutablePagesList = newList
                            }
                        }
                        pageToMoveIndex = null
                    }
                ) {
                    Text("ย้าย")
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToMoveIndex = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("จัดเรียงลำดับหน้าสแกน (${mutablePagesList.size} หน้า)")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mutablePagesList.forEachIndexed { index, page ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            ) {
                                AsyncImage(
                                    model = page.first,
                                    contentDescription = "Thumbnail $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topStart = 4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("#${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text("หน้าที่ ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                AssistChip(
                                    onClick = { pageToMoveIndex = index },
                                    label = { Text("ย้ายไปที่...", fontSize = 10.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = mutablePagesList.toMutableList()
                                        val movedItem = newList.removeAt(index)
                                        newList.add(index - 1, movedItem)
                                        mutablePagesList = newList
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Up", modifier = Modifier.size(18.dp))
                            }
                            
                            IconButton(
                                onClick = {
                                    if (index < mutablePagesList.size - 1) {
                                        val newList = mutablePagesList.toMutableList()
                                        val movedItem = newList.removeAt(index)
                                        newList.add(index + 1, movedItem)
                                        mutablePagesList = newList
                                    }
                                },
                                enabled = index < mutablePagesList.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Down", modifier = Modifier.size(18.dp))
                            }
                            
                            IconButton(
                                onClick = { onDelete(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(mutablePagesList) }) {
                Text("บันทึกการจัดเรียง", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFinishOptionsDialog(
    pagesCount: Int,
    previewUri: Uri? = null,
    initialTitle: String,
    initialFilter: String = "MAGIC_COLOR",
    initialAutoPdf: Boolean = true,
    initialRunOcr: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (com.example.ui.BatchScanConfig) -> Unit,
    onOpenReorder: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var autoGeneratePdf by remember { mutableStateOf(initialAutoPdf) }
    var runOcr by remember { mutableStateOf(initialRunOcr) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                "บันทึกและสร้าง PDF ($pagesCount หน้า)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("ชื่อเอกสาร / ไฟล์ PDF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )

                // Page Reorder shortcut button
                OutlinedButton(
                    onClick = onOpenReorder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ตรวจดูหรือจัดเรียงลำดับหน้า ($pagesCount หน้า)", fontSize = 12.sp)
                }

                // Real-time Preview Card of First Page with Selected Filter
                if (previewUri != null) {
                    val previewColorFilter = when (selectedFilter) {
                        "GRAYSCALE" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                        )
                        "BLACK_WHITE" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                2f, 0f, 0f, 0f, -128f,
                                0f, 2f, 0f, 0f, -128f,
                                0f, 0f, 2f, 0f, -128f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        "MAGIC_COLOR" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                1.25f, 0f, 0f, 0f, 12f,
                                0f, 1.25f, 0f, 0f, 12f,
                                0f, 0f, 1.25f, 0f, 12f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        else -> null
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = previewUri,
                                contentDescription = "พรีวิวภาพจริง",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                colorFilter = previewColorFilter
                            )

                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("พรีวิวสีตามจริง ⚡", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text("โหมดปรับแต่งสีภาพอัตโนมัติ (เรียลไทม์):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val filters = listOf(
                        "MAGIC_COLOR" to "ปรับคมชัด",
                        "ORIGINAL" to "ต้นฉบับ",
                        "BLACK_WHITE" to "ขาว-ดำ",
                        "GRAYSCALE" to "สีเทา"
                    )
                    filters.forEach { (mode, label) ->
                        FilterChip(
                            selected = selectedFilter == mode,
                            onClick = { selectedFilter = mode },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("สร้างไฟล์ PDF รวมชุดทันที", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("รวมหน้าทั้งหมด $pagesCount หน้าเป็นไฟล์ PDF ฉบับเดียว", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoGeneratePdf,
                                onCheckedChange = { autoGeneratePdf = it }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ถอดข้อความ OCR ทุกหน้า", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("สกัดตัวอักษรด้วย ML Kit เพื่อค้นหาข้อความ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = runOcr,
                                onCheckedChange = { runOcr = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        com.example.ui.BatchScanConfig(
                            title = title,
                            filterMode = selectedFilter,
                            runOcr = runOcr,
                            autoGeneratePdf = autoGeneratePdf
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("เริ่มประมวลผล & บันทึก PDF", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ถ่ายหน้าเพิ่ม")
            }
        }
    )
}

@Composable
fun CapturedPageLivePreviewDialog(
    pageNumber: Int,
    totalCount: Int,
    imageUri: Uri,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var previewFilter by remember { mutableStateOf("ORIGINAL") }

    val liveFilter = when (previewFilter) {
        "GRAYSCALE" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
        )
        "BLACK_WHITE" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                2f, 0f, 0f, 0f, -128f,
                0f, 2f, 0f, 0f, -128f,
                0f, 0f, 2f, 0f, -128f,
                0f, 0f, 0f, 1f, 0f
            ))
        )
        "MAGIC_COLOR" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                1.25f, 0f, 0f, 0f, 12f,
                0f, 1.25f, 0f, 0f, 12f,
                0f, 0f, 1.25f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            ))
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "พรีวิวภาพถ่าย หน้าที่ $pageNumber / $totalCount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "เรียลไทม์ ⚡",
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Page preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = liveFilter
                    )
                }

                Text("ทดลองดูผลลัพธ์ฟิลเตอร์แบบเรียลไทม์:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val filters = listOf(
                        "ORIGINAL" to "ต้นฉบับ",
                        "MAGIC_COLOR" to "ปรับคมชัด",
                        "BLACK_WHITE" to "ขาว-ดำ",
                        "GRAYSCALE" to "สีเทา"
                    )
                    filters.forEach { (mode, label) ->
                        FilterChip(
                            selected = previewFilter == mode,
                            onClick = { previewFilter = mode },
                            label = { Text(label, fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("ตกลง")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ลบหน้านี้")
            }
        }
    )
}

