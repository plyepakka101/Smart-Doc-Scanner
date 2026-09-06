package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ScannerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSettingsScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Pre-Scan Settings States
    val preScanFilterMode by viewModel.preScanFilterMode.collectAsState()
    val preScanRunOcr by viewModel.preScanRunOcr.collectAsState()
    val preScanAutoGeneratePdf by viewModel.preScanAutoGeneratePdf.collectAsState()
    val preScanDefaultFolder by viewModel.preScanDefaultFolder.collectAsState()
    val preScanScanMode by viewModel.preScanScanMode.collectAsState()
    val preScanAutoCapture by viewModel.preScanAutoCapture.collectAsState()
    val preScanAutoOrientation by viewModel.preScanAutoOrientation.collectAsState()
    val preScanCameraZoom by viewModel.preScanCameraZoom.collectAsState()
    val bookAspectRatio by viewModel.bookAspectRatio.collectAsState()
    val skipFinishConfirmDialog by viewModel.skipFinishConfirmDialog.collectAsState()
    val bluetoothShutterEnabled by viewModel.bluetoothShutterEnabled.collectAsState()

    // Image Enhancements
    val autoDewarpEnabled by viewModel.autoDewarpEnabled.collectAsState()
    val autoFingerRemovalEnabled by viewModel.autoFingerRemovalEnabled.collectAsState()
    val scanResolutionHigh by viewModel.scanResolutionHigh.collectAsState()

    // System & Storage
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    val savingStorageStatus by viewModel.savingStorageStatus.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ตั้งค่าก่อนสแกน",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "กำหนดค่าล่วงหน้าและจดจำไว้ตลอดการใช้งาน",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ย้อนกลับ"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    FilledTonalButton(
                        onClick = onNavigateToCamera,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เปิดกล้อง", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "บันทึกและจดจำการตั้งค่าอัตโนมัติแล้ว",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Button(
                        onClick = onNavigateToCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "เริ่มสแกนด้วยค่าที่กำหนดไว้",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notice Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ตั้งค่าก่อนสแกน (ไม่ต้องคอยตั้งหลังถ่าย)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "เลือกฟิลเตอร์, การรวม PDF, และการถอด OCR ไว้ล่วงหน้าได้เลย ระบบจะจดจำค่าเหล่านี้ไว้จนกว่าคุณจะเข้ามาเปลี่ยนอีกครั้ง",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Section 1: Default Color & Filter
            SettingsSectionCard(
                title = "1. โหมดสี & ฟิลเตอร์ภาพเริ่มต้น",
                subtitle = "เลือกโหมดปรับแต่งสีที่จะนำไปประมวลผลภาพอัตโนมัติทันทีที่สแกน",
                icon = Icons.Default.Palette
            ) {
                val filters = listOf(
                    Triple("MAGIC_COLOR", "ปรับสีคมชัด (แนะนำ)", "ปรับภาพพื้นหลังขาว ปรับตัวอักษรให้อ่านง่ายและคมชัดสูงสุด"),
                    Triple("ORIGINAL", "ต้นฉบับจริง", "คงสภาพสีและแสงจริงตามที่ถ่ายจากกล้อง"),
                    Triple("BLACK_WHITE", "ขาว-ดำ คอนทราสต์สูง", "เน้นตัวหนังสือดำสนิท ตัดแสงสะท้อน สำหรับเอกสารสัญญา"),
                    Triple("GRAYSCALE", "เฉดสีเทา (Grayscale)", "แปลงเป็นโทนเทาไล่เฉด เหมาะสำหรับตำราหรือภาพพิมพ์")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { (mode, label, desc) ->
                        val isSelected = preScanFilterMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setPreScanFilterMode(mode) }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setPreScanFilterMode(mode) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Automated Output Processing
            SettingsSectionCard(
                title = "2. การประมวลผลผลลัพธ์อัตโนมัติ",
                subtitle = "กำหนดสิ่งที่จะให้ระบบสร้างให้อัตโนมัติเมื่อกดเสร็จสิ้น",
                icon = Icons.Default.AutoAwesome
            ) {
                // Auto Generate PDF
                SettingsSwitchRow(
                    icon = Icons.Default.PictureAsPdf,
                    title = "รวมเป็นไฟล์ PDF อัตโนมัติ",
                    subtitle = "รวมหน้าสแกนทั้งหมดเข้าเป็นไฟล์เอกสาร PDF หลายหน้าทันทีในคลัง",
                    checked = preScanAutoGeneratePdf,
                    onCheckedChange = { viewModel.setPreScanAutoGeneratePdf(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Run OCR
                SettingsSwitchRow(
                    icon = Icons.Default.DocumentScanner,
                    title = "ถอดข้อความ OCR อัตโนมัติ (ไทย & อังกฤษ)",
                    subtitle = "สกัดตัวอักษรทุกหน้าด้วย ML Kit ทันที ทำให้สามารถค้นหาคำและคัดลอกข้อความได้",
                    checked = preScanRunOcr,
                    onCheckedChange = { viewModel.setPreScanRunOcr(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Skip Confirm Dialog (1-Tap Instant Finish)
                SettingsSwitchRow(
                    icon = Icons.Default.TouchApp,
                    title = "บันทึกรวดเร็วทันที 1-Tap (ข้ามหน้าต่างสรุป)",
                    subtitle = "เมื่อกดเสร็จสิ้น จะเริ่มประมวลผลและสร้างเอกสารทันทีตามการตั้งค่านี้ โดยไม่ต้องกดยืนยันซ้ำ",
                    checked = skipFinishConfirmDialog,
                    onCheckedChange = { viewModel.setSkipFinishConfirmDialog(it) }
                )
            }

            // Section 3: Smart AI Image Enhancement
            SettingsSectionCard(
                title = "3. การปรับแต่งภาพอัจฉริยะ (AI Enhancement)",
                subtitle = "ระบบปัญญาประดิษฐ์ซ่อมแซมภาพและลบลายนิ้วมืออัตโนมัติ",
                icon = Icons.Default.AutoFixHigh
            ) {
                // Auto Dewarp
                SettingsSwitchRow(
                    icon = Icons.Default.CropRotate,
                    title = "ซ่อมแซมภาพ & ปรับระนาบแบนเรียบ (Auto De-warp)",
                    subtitle = "ปรับแก้หน้ากระดาษที่โค้งงอหรือถ่ายเอียงให้แบนตรงอัตโนมัติ",
                    checked = autoDewarpEnabled,
                    onCheckedChange = { viewModel.setAutoDewarpEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Auto Finger Removal
                SettingsSwitchRow(
                    icon = Icons.Default.FrontHand,
                    title = "ลบลายนิ้วมือขอบภาพอัตโนมัติ",
                    subtitle = "ตรวจจับและลบนิ้วมือที่จับขอบหนังสือหรือกระดาษออกให้เรียบเนียน",
                    checked = autoFingerRemovalEnabled,
                    onCheckedChange = { viewModel.setAutoFingerRemovalEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Scan Resolution
                SettingsSwitchRow(
                    icon = Icons.Default.HighQuality,
                    title = "ความละเอียดสูงพิเศษ 600 DPI",
                    subtitle = if (scanResolutionHigh) "เปิดใช้งาน 600 DPI คมชัดสูงสุดสำหรับเอกสารสำคัญหรือพิมพ์" else "ปิดใช้งาน (ใช้ 300 DPI คมชัดระดับมาตรฐานและประหยัดพื้นที่โทรศัพท์)",
                    checked = scanResolutionHigh,
                    onCheckedChange = { viewModel.setScanResolutionHigh(it) }
                )
            }

            // Section 4: Camera & Scanning Behavior
            SettingsSectionCard(
                title = "4. กล้อง & ท่าทางการสแกน",
                subtitle = "กำหนดค่าเริ่มต้นของกล้องเพื่อให้พร้อมถ่ายได้รวดเร็วที่สุด",
                icon = Icons.Default.CameraAlt
            ) {
                // Default Scan Mode Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "โหมดการสแกนเริ่มต้นเมื่อเปิดกล้อง:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val modes = listOf(
                        Triple("BOOK_2PAGE", "หนังสือ 2 หน้า ⭐", "แยกซ้าย-ขวาอัตโนมัติ"),
                        Triple("MULTI_PAGE", "หลายหน้า (Batch)", "ถ่ายต่อเนื่องรวมชุด"),
                        Triple("SINGLE", "หน้าเดียว", "สแกนเร็วทีละหน้า")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        modes.forEach { (mode, title, desc) ->
                            val isSelected = preScanScanMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setPreScanScanMode(mode) }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = desc,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Book Aspect Ratio Preset for 2-Page mode
                    if (preScanScanMode == "BOOK_2PAGE") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "สัดส่วนของหนังสือ (Book Aspect Ratio):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val ratioOptions = listOf(
                            Triple(1.414f, "A4/A5 (1.41:1)", "มาตรฐานทั่วไป"),
                            Triple(1.333f, "4:3 (พ็อกเก็ตบุ๊ค)", "นวนิยาย / พ็อกเก็ต"),
                            Triple(1.500f, "1.5:1 (หน้ากว้าง)", "สมุดภาพ / ตำราใหญ่")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ratioOptions.forEach { (ratio, title, desc) ->
                                val isSelected = kotlin.math.abs(bookAspectRatio - ratio) < 0.04f
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setBookAspectRatio(ratio) }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Auto Orientation Detection
                SettingsSwitchRow(
                    icon = Icons.Default.ScreenRotation,
                    title = "สลับโหมดอัตโนมัติตามแนวเครื่อง",
                    subtitle = "ถือเครื่องแนวตั้ง = โหมดหน้าเดียว, ถือเครื่องแนวนอน = โหมดหนังสือ 2 หน้าอัตโนมัติ",
                    checked = preScanAutoOrientation,
                    onCheckedChange = { viewModel.setPreScanAutoOrientation(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Auto Capture
                SettingsSwitchRow(
                    icon = Icons.Default.FlashAuto,
                    title = "ถ่ายภาพอัตโนมัติเมื่อเปิดหน้าใหม่ (Auto-Capture)",
                    subtitle = "กล้องจะโฟกัสและจับภาพให้อัตโนมัติเมื่อตรวจพบการพลิกหน้าใหม่โดยไม่ต้องกดชัตเตอร์",
                    checked = preScanAutoCapture,
                    onCheckedChange = { viewModel.setPreScanAutoCapture(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Default Zoom
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ระยะกล้องเริ่มต้นสำหรับหนังสือ:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "แนะนำ 1.4x ใกล้ ⭐ เพื่อให้ตัวหนังสือเต็มกรอบและคมชัดที่สุด",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val zoomRatios = listOf(
                        Pair(1.0f, "1.0x ปกติ"),
                        Pair(1.2f, "1.2x"),
                        Pair(1.35f, "1.4x ใกล้ ⭐"),
                        Pair(1.8f, "1.8x"),
                        Pair(2.0f, "2.0x")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        zoomRatios.forEach { (ratio, label) ->
                            val isSelected = kotlin.math.abs(preScanCameraZoom - ratio) < 0.08f
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setPreScanCameraZoom(ratio) },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Section 5: Bluetooth Shutter Integration (Direct In-App Scanning)
            SettingsSectionCard(
                title = "5. ชัตเตอร์ไร้สายบลูทูธ (Bluetooth Remote Shutter)",
                subtitle = "กดปุ่มชัตเตอร์ไร้สายหรือปุ่มปรับเสียงเพื่อสั่งสแกนเอกสารในแอปโดยตรงทันที ไม่ต้องเรียกแอปกล้องภายนอก",
                icon = Icons.Default.BluetoothConnected
            ) {
                // Bluetooth Remote Shutter
                SettingsSwitchRow(
                    icon = Icons.Default.Bluetooth,
                    title = "สแกนด้วยชัตเตอร์บลูทูธ & ปุ่มปรับเสียงในแอปโดยตรง",
                    subtitle = "เปิดใช้งาน: เมื่อกดปุ่มรีโมทบลูทูธไร้สาย (เช่น รีโมทเซลฟี่ ไม้เซลฟี่ กิมบอล) หรือปุ่มเพิ่ม/ลดเสียงบนเครื่อง ระบบจะสั่งสแกนเอกสารในแอปทันที โดยไม่ต้องเรียกแอปกล้องภายนอก",
                    checked = bluetoothShutterEnabled,
                    onCheckedChange = { viewModel.setBluetoothShutterEnabled(it) }
                )
            }

            // Section 6: Default Folder
            SettingsSectionCard(
                title = "6. โฟลเดอร์จัดเก็บเริ่มต้น",
                subtitle = "เลือกโฟลเดอร์ที่จะบันทึกเอกสารสแกนใหม่ลงไปโดยอัตโนมัติ",
                icon = Icons.Default.Folder
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val availableFolders = customFolders.filter { it != "ทั้งหมด" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableFolders.take(4).forEach { folder ->
                            val isSelected = preScanDefaultFolder == folder
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setPreScanDefaultFolder(folder) },
                                label = { Text(folder, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    if (availableFolders.size > 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableFolders.drop(4).forEach { folder ->
                                val isSelected = preScanDefaultFolder == folder
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setPreScanDefaultFolder(folder) },
                                    label = { Text(folder, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Section 7: System & Storage
            SettingsSectionCard(
                title = "7. ธีมระบบ & เพิ่มความจุโทรศัพท์",
                subtitle = "ปรับแต่งหน้าตาและทำความสะอาดหน่วยความจำเครื่อง",
                icon = Icons.Default.SettingsApplications
            ) {
                // Dark Mode
                SettingsSwitchRow(
                    icon = if (darkModeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "โหมดมืด (Dark Mode)",
                    subtitle = "เปลี่ยนธีมแอปพลิเคชันเป็นโทนสีมืดเพื่อถนอมสายตา",
                    checked = darkModeEnabled,
                    onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Storage Clean Up
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "เพิ่มความจุโทรศัพท์",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ลบรูปภาพต้นฉบับทั้งหมด และเก็บเฉพาะไฟล์สแกนที่ประมวลผลแล้วเพื่อประหยัดพื้นที่",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.deleteRawImagesToSaveStorage(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("ล้างไฟล์", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (savingStorageStatus != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = savingStorageStatus ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
