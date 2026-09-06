package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.ScannedPage
import com.example.ui.ScannerViewModel
import com.example.ui.components.ZoomableImage
import com.example.util.ImageProcessor
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageWorkspaceScreen(
    page: ScannedPage,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Live adjustable properties
    var selectedFilter by remember { mutableStateOf(page.filterMode) }
    var brightness by remember { mutableFloatStateOf(page.brightness) }
    var applyDewarp by remember { mutableStateOf(page.hasDeWarp) }
    var applyFingerRemoval by remember { mutableStateOf(page.hasFingerRemoved) }

    // Interactive Crop Points (0.0 to 1.0)
    var cropTopLeft by remember { mutableStateOf(PointF(page.cropLeft.coerceIn(0f, 1f), page.cropTop.coerceIn(0f, 1f))) }
    var cropTopRight by remember { mutableStateOf(PointF(page.cropRight.coerceIn(0f, 1f), page.cropTop.coerceIn(0f, 1f))) }
    var cropBottomRight by remember { mutableStateOf(PointF(page.cropRight.coerceIn(0f, 1f), page.cropBottom.coerceIn(0f, 1f))) }
    var cropBottomLeft by remember { mutableStateOf(PointF(page.cropLeft.coerceIn(0f, 1f), page.cropBottom.coerceIn(0f, 1f))) }
    var isCropActive by remember { mutableStateOf(true) }

    // Active View Tab: 0 = Live Processed Preview, 1 = Crop Boundaries, 2 = Split Comparison (Before / After)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Hold-to-compare instant toggle
    var isHoldingOriginal by remember { mutableStateOf(false) }

    // Interactive Split View slider position (0f to 1f)
    var splitSliderRatio by remember { mutableFloatStateOf(0.5f) }

    // Full original image decoded from disk
    val rawBitmap = remember(page.originalImagePath) {
        val file = File(page.originalImagePath)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    // Downscaled preview source (max 1200px) for ultra-fast 60fps real-time OpenCV rendering
    val previewSourceBitmap = remember(rawBitmap) {
        if (rawBitmap == null) null
        else {
            val maxDim = 1200
            val w = rawBitmap.width
            val h = rawBitmap.height
            if (w > maxDim || h > maxDim) {
                val scale = maxDim.toFloat() / kotlin.math.max(w, h)
                Bitmap.createScaledBitmap(rawBitmap, (w * scale).toInt().coerceAtLeast(100), (h * scale).toInt().coerceAtLeast(100), true)
            } else {
                rawBitmap
            }
        }
    }

    // Real-time processed preview bitmap
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var liveCroppedMiniBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingPreview by remember { mutableStateOf(false) }

    // Continuous Real-Time Processing Loop
    LaunchedEffect(
        previewSourceBitmap,
        selectedFilter,
        brightness,
        applyDewarp,
        applyFingerRemoval,
        cropTopLeft,
        cropTopRight,
        cropBottomRight,
        cropBottomLeft,
        isCropActive
    ) {
        val source = previewSourceBitmap ?: return@LaunchedEffect
        isProcessingPreview = true

        val cropPoints = if (isCropActive) listOf(cropTopLeft, cropTopRight, cropBottomRight, cropBottomLeft) else null

        // 1. Process full rectified result in background
        val processed = withContext(Dispatchers.Default) {
            ImageProcessor.processPage(
                source = source,
                filterMode = selectedFilter,
                applyDeWarp = applyDewarp,
                applyFingerRemoval = applyFingerRemoval,
                cropPoints = cropPoints,
                brightness = brightness
            )
        }
        previewBitmap = processed
        liveCroppedMiniBitmap = processed
        isProcessingPreview = false
    }

    // Hardware-accelerated Compose color matrix for instantaneous 0ms slider response
    val liveColorFilter = remember(brightness, selectedFilter) {
        val bOffset = brightness * 1.5f // -150 to +150
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, bOffset,
                0f, 1f, 0f, 0f, bOffset,
                0f, 0f, 1f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "พรีวิวและแก้ไขภาพแบบเรียลไทม์",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isProcessingPreview) Color(0xFFFFB300) else Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isProcessingPreview) "กำลังอัปเดต..." else "แสดงผลสด 100% Real-Time",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val cropPoints = if (isCropActive) listOf(cropTopLeft, cropTopRight, cropBottomRight, cropBottomLeft) else null
                            viewModel.reprocessPage(
                                page = page,
                                filter = selectedFilter,
                                dewarp = applyDewarp,
                                finger = applyFingerRemoval,
                                cropPoints = cropPoints,
                                brightness = brightness
                            )
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "บันทึก", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("บันทึก", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Visual Mode Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("พรีวิวผลลัพธ์", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ตัดขอบกระดาษ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ก่อน-หลัง (Split)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                )
            }

            // PRIMARY REAL-TIME VIEWPORT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: LIVE PROCESSED PREVIEW (Full pinch-zoom & pan)
                        val activeDisplayBitmap = if (isHoldingOriginal) rawBitmap else (previewBitmap ?: rawBitmap)

                        if (activeDisplayBitmap != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ZoomableImage(
                                    bitmap = activeDisplayBitmap,
                                    contentDescription = "Real-time Live Preview",
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Live Status Badge (Top Right)
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isHoldingOriginal) "กำลังดู: ภาพถ่ายต้นฉบับ" else "พรีวิวสดเรียลไทม์",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isProcessingPreview && !isHoldingOriginal) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            CircularProgressIndicator(
                                                color = Color(0xFF00E676),
                                                modifier = Modifier.size(10.dp),
                                                strokeWidth = 1.5.dp
                                            )
                                        }
                                    }
                                }

                                // Instant Hold-To-Compare Button (Bottom Left)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(20.dp),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isHoldingOriginal = true
                                                    tryAwaitRelease()
                                                    isHoldingOriginal = false
                                                }
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "แตะค้างดูต้นฉบับ",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    1 -> {
                        // TAB 1: CROP & PERSPECTIVE ADJUSTMENT WITH LIVE MAGNIFIER & PICTURE-IN-PICTURE
                        val displaySource = previewSourceBitmap ?: rawBitmap

                        if (displaySource != null) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val containerW = maxWidth
                                val containerH = maxHeight

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Original Uncropped Image
                                    Image(
                                        bitmap = displaySource.asImageBitmap(),
                                        contentDescription = "Uncropped Canvas",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = liveColorFilter
                                    )

                                    // Real-Time Draggable Crop Overlay with Magnifier Loupe
                                    RealTimeCropOverlay(
                                        sourceBitmap = displaySource,
                                        tl = cropTopLeft,
                                        tr = cropTopRight,
                                        br = cropBottomRight,
                                        bl = cropBottomLeft,
                                        onUpdate = { t, r, b, l ->
                                            cropTopLeft = t
                                            cropTopRight = r
                                            cropBottomRight = b
                                            cropBottomLeft = l
                                            isCropActive = true
                                        }
                                    )

                                    // Floating Picture-in-Picture Mini-Preview of Cropped Document
                                    if (liveCroppedMiniBitmap != null) {
                                        Card(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(12.dp)
                                                .size(width = 110.dp, height = 150.dp)
                                                .clickable { selectedTab = 0 },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E676)),
                                            elevation = CardDefaults.cardElevation(8.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF00E676))
                                                        .padding(vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "พรีวิวผลลัพธ์ตัดขอบ ⚡",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black
                                                    )
                                                }
                                                Image(
                                                    bitmap = liveCroppedMiniBitmap!!.asImageBitmap(),
                                                    contentDescription = "Mini live cropped preview",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.Black.copy(alpha = 0.8f))
                                                        .padding(vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "แตะเพื่อดูเต็มจอ >",
                                                        fontSize = 8.sp,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    2 -> {
                        // TAB 2: INTERACTIVE BEFORE / AFTER SPLIT COMPARISON
                        val origBmp = previewSourceBitmap ?: rawBitmap
                        val procBmp = previewBitmap ?: rawBitmap

                        if (origBmp != null && procBmp != null) {
                            InteractiveSplitComparisonView(
                                originalBitmap = origBmp,
                                processedBitmap = procBmp,
                                splitRatio = splitSliderRatio,
                                onSplitRatioChange = { splitSliderRatio = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // TOOLBOX ADJUSTMENT PANELS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Action Row for Crop Mode
                    if (selectedTab == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val src = previewSourceBitmap ?: rawBitmap
                                    if (src != null) {
                                        coroutineScope.launch {
                                            val quad = ImageProcessor.detectAutoDocumentQuad(src)
                                            if (quad.size == 4) {
                                                cropTopLeft = quad[0]
                                                cropTopRight = quad[1]
                                                cropBottomRight = quad[2]
                                                cropBottomLeft = quad[3]
                                                isCropActive = true
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ตรวจจับขอบอัตโนมัติ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    cropTopLeft = PointF(0f, 0f)
                                    cropTopRight = PointF(1f, 0f)
                                    cropBottomRight = PointF(1f, 1f)
                                    cropBottomLeft = PointF(0f, 1f)
                                    isCropActive = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("รีเซ็ตเต็มหน้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // A. Color Filter Presets Pick Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "1. ฟิลเตอร์ปรับสีเอกสาร (Real-Time Filters)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "พรีวิวเรียลไทม์ ⚡",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterButton(
                            label = "สีสดคมชัด",
                            active = selectedFilter == "MAGIC_COLOR" || selectedFilter == "ENHANCED",
                            onClick = { selectedFilter = "MAGIC_COLOR" },
                            icon = Icons.Default.AutoFixHigh,
                            modifier = Modifier.weight(1f)
                        )
                        FilterButton(
                            label = "ต้นฉบับจริง",
                            active = selectedFilter == "ORIGINAL",
                            onClick = { selectedFilter = "ORIGINAL" },
                            icon = Icons.Default.FilterNone,
                            modifier = Modifier.weight(1f)
                        )
                        FilterButton(
                            label = "ขาว-ดำ B&W",
                            active = selectedFilter == "BLACK_WHITE",
                            onClick = { selectedFilter = "BLACK_WHITE" },
                            icon = Icons.Default.FilterBAndW,
                            modifier = Modifier.weight(1f)
                        )
                        FilterButton(
                            label = "เฉดสีเทา",
                            active = selectedFilter == "GRAYSCALE",
                            onClick = { selectedFilter = "GRAYSCALE" },
                            icon = Icons.Default.FormatPaint,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // B. Brightness Slider with Instant Response
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Brightness6,
                                    contentDescription = "Brightness",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ปรับความสว่าง (Brightness):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (brightness > 0) "+${brightness.roundToInt()}" else "${brightness.roundToInt()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = -100f..100f,
                                modifier = Modifier.weight(1f)
                            )
                            if (brightness != 0f) {
                                IconButton(
                                    onClick = { brightness = 0f },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "รีเซ็ตความสว่าง",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // C. AI Straightening & Finger Eraser
                    Text(
                        "2. ปรับสภาพและลบจุดกีดขวาง (AI Enhancements)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ปรับหน้าโค้งให้แบนเรียบ (AI Page Flattening)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("สกัดรอยนูนโค้งใกล้สันหนังสือเพื่อให้อ่านบรรทัดเรียงตรงง่าย", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = applyDewarp, onCheckedChange = { applyDewarp = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ตรวจจับและลบลายนิ้วมือ (Finger Eraser)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("ลบลายนิ้วมือที่เผลอประคองขอบกระดาษในเฟรมออกอัตโนมัติ", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(checked = applyFingerRemoval, onCheckedChange = { applyFingerRemoval = it })
                    }
                }
            }
        }
    }
}

/**
 * Interactive Split-view slider allowing horizontal drag to compare original photo vs processed scan.
 */
@Composable
fun InteractiveSplitComparisonView(
    originalBitmap: Bitmap,
    processedBitmap: Bitmap,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val splitX = (widthPx * splitRatio).coerceIn(10f, widthPx - 10f)

        // Bottom Layer: Processed Scan
        Image(
            bitmap = processedBitmap.asImageBitmap(),
            contentDescription = "Processed scan",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Top Layer: Raw Original Photo clipped to the left of the split line
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Clip drawing to left side of the split line
                clipRect(left = 0f, top = 0f, right = splitX, bottom = heightPx) {
                    val origImageBitmap = originalBitmap.asImageBitmap()
                    // Fit image into the canvas
                    val srcW = originalBitmap.width.toFloat()
                    val srcH = originalBitmap.height.toFloat()
                    val scale = kotlin.math.min(widthPx / srcW, heightPx / srcH)
                    val dstW = srcW * scale
                    val dstH = srcH * scale
                    val dstX = (widthPx - dstW) / 2f
                    val dstY = (heightPx - dstH) / 2f

                    drawImage(
                        image = origImageBitmap,
                        dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                        dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
                    )
                }
            }
        }

        // Split Divider Line & Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset((splitX - 24.dp.toPx()).roundToInt(), 0) }
                .width(48.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newSplitX = (splitX + dragAmount.x).coerceIn(10f, widthPx - 10f)
                        onSplitRatioChange(newSplitX / widthPx)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Divider vertical line
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )

            // Double Arrow Circular Thumb
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shadowElevation = 8.dp,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = "Swipe comparison",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Left Label: "ก่อน (ต้นฉบับ)"
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("ก่อน: ภาพถ่ายดิบ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        // Right Label: "หลัง (สแกนเรียลไทม์)"
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("หลัง: ปรับแต่งเรียลไทม์ ⚡", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Interactive perspective crop boundary overlay with draggable handles and real-time Magnifier Loupe.
 */
@Composable
fun BoxScope.RealTimeCropOverlay(
    sourceBitmap: Bitmap,
    tl: PointF,
    tr: PointF,
    br: PointF,
    bl: PointF,
    onUpdate: (PointF, PointF, PointF, PointF) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        var pTL by remember(tl, widthPx, heightPx) { mutableStateOf(Offset(tl.x * widthPx, tl.y * heightPx)) }
        var pTR by remember(tr, widthPx, heightPx) { mutableStateOf(Offset(tr.x * widthPx, tr.y * heightPx)) }
        var pBR by remember(br, widthPx, heightPx) { mutableStateOf(Offset(br.x * widthPx, br.y * heightPx)) }
        var pBL by remember(bl, widthPx, heightPx) { mutableStateOf(Offset(bl.x * widthPx, bl.y * heightPx)) }

        // Currently dragged corner for Magnifier Loupe
        var activeHandle by remember { mutableStateOf<String?>(null) }
        var activeHandlePos by remember { mutableStateOf(Offset.Zero) }

        val strokeWidthPx = with(LocalDensity.current) { 2.5.dp.toPx() }

        // 1. Draw Quad Polygon & Translucent Mask
        Canvas(modifier = Modifier.fillMaxSize()) {
            val quadPath = Path().apply {
                moveTo(pTL.x, pTL.y)
                lineTo(pTR.x, pTR.y)
                lineTo(pBR.x, pBR.y)
                lineTo(pBL.x, pBL.y)
                close()
            }

            // High-visibility green quad outline & fill
            drawPath(quadPath, color = Color(0x2200E676))
            drawPath(quadPath, color = Color(0xFF00E676), style = Stroke(width = strokeWidthPx))

            // Draw midpoint guide marks
            val midTop = Offset((pTL.x + pTR.x) / 2f, (pTL.y + pTR.y) / 2f)
            val midRight = Offset((pTR.x + pBR.x) / 2f, (pTR.y + pBR.y) / 2f)
            val midBottom = Offset((pBL.x + pBR.x) / 2f, (pBL.y + pBR.y) / 2f)
            val midLeft = Offset((pTL.x + pBL.x) / 2f, (pTL.y + pBL.y) / 2f)

            drawCircle(Color.White, radius = 4.dp.toPx(), center = midTop)
            drawCircle(Color.White, radius = 4.dp.toPx(), center = midRight)
            drawCircle(Color.White, radius = 4.dp.toPx(), center = midBottom)
            drawCircle(Color.White, radius = 4.dp.toPx(), center = midLeft)
        }

        val handleRadiusDp = 22.dp
        val handleRadiusPx = with(LocalDensity.current) { handleRadiusDp.toPx() }

        @Composable
        fun DraggableCornerHandle(
            position: Offset,
            label: String,
            onDragStart: () -> Unit,
            onDragEnd: () -> Unit,
            onDrag: (Offset) -> Unit
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (position.x - handleRadiusPx).roundToInt(),
                            (position.y - handleRadiusPx).roundToInt()
                        )
                    }
                    .size(handleRadiusDp * 2)
                    .pointerInput(label) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = (position.x + dragAmount.x).coerceIn(0f, widthPx)
                                val newY = (position.y + dragAmount.y).coerceIn(0f, heightPx)
                                onDrag(Offset(newX, newY))
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E676),
                    border = androidx.compose.foundation.BorderStroke(2.5.dp, Color.White),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Top-Left
        DraggableCornerHandle(
            position = pTL,
            label = "TL",
            onDragStart = {
                activeHandle = "TL"
                activeHandlePos = pTL
            },
            onDragEnd = { activeHandle = null },
            onDrag = { newPos ->
                pTL = newPos
                activeHandlePos = newPos
                onUpdate(PointF(newPos.x / widthPx, newPos.y / heightPx), tr, br, bl)
            }
        )

        // Top-Right
        DraggableCornerHandle(
            position = pTR,
            label = "TR",
            onDragStart = {
                activeHandle = "TR"
                activeHandlePos = pTR
            },
            onDragEnd = { activeHandle = null },
            onDrag = { newPos ->
                pTR = newPos
                activeHandlePos = newPos
                onUpdate(tl, PointF(newPos.x / widthPx, newPos.y / heightPx), br, bl)
            }
        )

        // Bottom-Right
        DraggableCornerHandle(
            position = pBR,
            label = "BR",
            onDragStart = {
                activeHandle = "BR"
                activeHandlePos = pBR
            },
            onDragEnd = { activeHandle = null },
            onDrag = { newPos ->
                pBR = newPos
                activeHandlePos = newPos
                onUpdate(tl, tr, PointF(newPos.x / widthPx, newPos.y / heightPx), bl)
            }
        )

        // Bottom-Left
        DraggableCornerHandle(
            position = pBL,
            label = "BL",
            onDragStart = {
                activeHandle = "BL"
                activeHandlePos = pBL
            },
            onDragEnd = { activeHandle = null },
            onDrag = { newPos ->
                pBL = newPos
                activeHandlePos = newPos
                onUpdate(tl, tr, br, PointF(newPos.x / widthPx, newPos.y / heightPx))
            }
        )

        // REAL-TIME MAGNIFIER LOUPE WHILE DRAGGING
        if (activeHandle != null) {
            val loupeSizeDp = 100.dp
            val loupeSizePx = with(LocalDensity.current) { loupeSizeDp.toPx() }
            val loupeOffsetYDp = if (activeHandlePos.y > loupeSizePx + 40f) -75.dp else 75.dp

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (activeHandlePos.x - loupeSizePx / 2f).roundToInt().coerceIn(10, (widthPx - loupeSizePx - 10).roundToInt()),
                            (activeHandlePos.y + loupeOffsetYDp.toPx()).roundToInt().coerceIn(10, (heightPx - loupeSizePx - 10).roundToInt())
                        )
                    }
                    .size(loupeSizeDp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(3.dp, Color(0xFF00E676), CircleShape)
            ) {
                // Magnified image canvas centered around activeHandlePos
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val zoomFactor = 2.5f
                    val centerLoupe = Offset(size.width / 2f, size.height / 2f)

                    val srcW = sourceBitmap.width.toFloat()
                    val srcH = sourceBitmap.height.toFloat()
                    val scale = kotlin.math.min(widthPx / srcW, heightPx / srcH)
                    val dstW = srcW * scale
                    val dstH = srcH * scale
                    val dstX = (widthPx - dstW) / 2f
                    val dstY = (heightPx - dstH) / 2f

                    // Map active handle to source bitmap coordinates
                    val bmpX = (activeHandlePos.x - dstX) / scale
                    val bmpY = (activeHandlePos.y - dstY) / scale

                    val srcRectLeft = (bmpX - (size.width / (2f * zoomFactor * scale))).coerceIn(0f, srcW)
                    val srcRectTop = (bmpY - (size.height / (2f * zoomFactor * scale))).coerceIn(0f, srcH)
                    val srcRectW = (size.width / (zoomFactor * scale)).coerceAtMost(srcW - srcRectLeft)
                    val srcRectH = (size.height / (zoomFactor * scale)).coerceAtMost(srcH - srcRectTop)

                    val srcIntOffset = IntOffset(srcRectLeft.roundToInt(), srcRectTop.roundToInt())
                    val srcIntSize = IntSize(srcRectW.roundToInt().coerceAtLeast(1), srcRectH.roundToInt().coerceAtLeast(1))

                    drawImage(
                        image = sourceBitmap.asImageBitmap(),
                        srcOffset = srcIntOffset,
                        srcSize = srcIntSize,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )

                    // Target Crosshairs
                    drawLine(
                        color = Color.Red,
                        start = Offset(centerLoupe.x - 14.dp.toPx(), centerLoupe.y),
                        end = Offset(centerLoupe.x + 14.dp.toPx(), centerLoupe.y),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color.Red,
                        start = Offset(centerLoupe.x, centerLoupe.y - 14.dp.toPx()),
                        end = Offset(centerLoupe.x, centerLoupe.y + 14.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = centerLoupe)
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        shadowElevation = if (active) 2.dp else 0.dp,
        modifier = modifier.height(42.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

