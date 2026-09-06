package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Document
import com.example.data.ScannedPage
import com.example.ui.ScannerViewModel
import com.example.ui.components.rememberGalleryPickerLauncher
import com.example.util.ExportUtility
import java.io.File
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.util.Date

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListScreen(
    viewModel: ScannerViewModel,
    onNavigateToPreview: (Document) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as Activity
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages?.map { it.imageUri }
            if (!pages.isNullOrEmpty()) {
                viewModel.addExternalPages(pages) { doc ->
                    onNavigateToPreview(doc)
                }
            }
        }
    }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredDocs by viewModel.filteredDocuments.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val allDocuments by viewModel.allDocuments.collectAsState()

    val preScanFilterMode by viewModel.preScanFilterMode.collectAsState()
    val preScanAutoGeneratePdf by viewModel.preScanAutoGeneratePdf.collectAsState()
    val preScanRunOcr by viewModel.preScanRunOcr.collectAsState()

    var showRenameDialog by remember { mutableStateOf<Document?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Document?>(null) }
    var showMoveToFolderDoc by remember { mutableStateOf<Document?>(null) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showFolderManagementDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var selectedFolderForNewScan by remember { mutableStateOf("ทั่วไป") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var isAutofocusingBeforeScan by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }

    var selectedDocIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isExportingBatch by remember { mutableStateOf(false) }

    // Image Import feature states
    var showImportConfigDialog by remember { mutableStateOf(false) }
    var pendingImportUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var importDocTitle by remember { mutableStateOf("") }
    var importTargetFolder by remember { mutableStateOf("ทั่วไป") }
    var importIsBookMode by remember { mutableStateOf(false) }

    val galleryPicker = rememberGalleryPickerLauncher { uris ->
        if (uris.isNotEmpty()) {
            pendingImportUris = uris
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            importDocTitle = "นำเข้า_${dateFormat.format(Date())}"
            importTargetFolder = if (selectedFolder != "ทั้งหมด") selectedFolder else "ทั่วไป"
            importIsBookMode = false
            showImportConfigDialog = true
        }
    }

    val autoFingerRemovalEnabled by viewModel.autoFingerRemovalEnabled.collectAsState()
    val autoDewarpEnabled by viewModel.autoDewarpEnabled.collectAsState()
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    val scanResolutionHigh by viewModel.scanResolutionHigh.collectAsState()
    val savingStorageStatus by viewModel.savingStorageStatus.collectAsState()
    val isBatchScanningMode by viewModel.isBatchScanningMode.collectAsState()
    val isProcessingBatch by viewModel.isProcessingBatch.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncSummary by viewModel.syncSummary.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchTotal by viewModel.batchTotal.collectAsState()
    val batchStatusText by viewModel.batchStatusText.collectAsState()
    val batchResult by viewModel.batchResultState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = selectedDocIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedDocIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                            Text(
                                text = "เลือก ${selectedDocIds.size} ชุดเอกสาร",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    selectedDocIds = if (selectedDocIds.size == filteredDocs.size) {
                                        emptySet()
                                    } else {
                                        filteredDocs.map { it.first.id }.toSet()
                                    }
                                }
                            ) {
                                Text(if (selectedDocIds.size == filteredDocs.size) "ยกเลิก" else "เลือกทั้งหมด", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { showBatchMoveDialog = true },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ย้ายโฟลเดอร์", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isExportingBatch = true
                                        val selectedPairs = filteredDocs.filter { selectedDocIds.contains(it.first.id) }
                                        val pdfFile = if (selectedPairs.size == 1) {
                                            ExportUtility.exportToPdf(context, selectedPairs.first().first, selectedPairs.first().second)
                                        } else {
                                            ExportUtility.exportMultipleDocumentsToPdf(context, selectedPairs)
                                        }
                                        isExportingBatch = false
                                        if (pdfFile != null) {
                                            ExportUtility.shareFile(context, pdfFile, "application/pdf")
                                            selectedDocIds = emptySet()
                                        }
                                    }
                                },
                                enabled = !isExportingBatch,
                                shape = RoundedCornerShape(50)
                            ) {
                                if (isExportingBatch) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("กำลังสร้าง PDF...", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ส่งออก PDF & แชร์", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                // App Logo Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "App Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "Smart Doc Scanner",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Stats / Badge indicating local layout
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "AI PRO",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            galleryPicker.launch()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "นำเข้ารูปภาพจากคลัง",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "มุมมองรายการ" else "มุมมองแกลเลอรี",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.setDarkModeEnabled(!darkModeEnabled) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (darkModeEnabled) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (darkModeEnabled) "เปลี่ยนเป็นโหมดสว่าง" else "เปลี่ยนเป็นโหมดมืด",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { showCloudSyncDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (syncStatus is com.example.data.SyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "สำรองและซิงค์คลาวด์ (Firestore)",
                                tint = if (syncSummary.autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "ตั้งค่าก่อนสแกน (Pre-Scan Settings)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modern Search Field with generous spacing & clean background
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("ค้นหาชื่อไฟล์ หรือ ข้อความในเอกสาร (OCR)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        disabledContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Folder Filter Chip Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedFolder == "ทั้งหมด",
                            onClick = { viewModel.setSelectedFolder("ทั้งหมด") },
                            label = {
                                Text(
                                    text = "ทั้งหมด (${allDocuments.size})",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedFolder == "ทั้งหมด") FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    items(customFolders) { folderName ->
                        val count = allDocuments.count { it.folder == folderName }
                        FilterChip(
                            selected = selectedFolder == folderName,
                            onClick = { viewModel.setSelectedFolder(folderName) },
                            label = {
                                Text(
                                    text = "$folderName ($count)",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedFolder == folderName) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { showCreateFolderDialog = true },
                            label = { Text("+ โฟลเดอร์ใหม่", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        AssistChip(
                            onClick = { showFolderManagementDialog = true },
                            label = { Text("จัดการโฟลเดอร์", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderShared,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Import Images Button (Gallery / System Apps)
                FloatingActionButton(
                    onClick = {
                        galleryPicker.launch()
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Import Images", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("นำเข้ารูปภาพ (Gallery)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Quick Batch Multi-Page Scan Button
                FloatingActionButton(
                    onClick = {
                        viewModel.setBatchScanningMode(true)
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        newFolderName = "Scan_${dateFormat.format(Date())}"
                        isAutofocusingBeforeScan = true
                        coroutineScope.launch {
                            delay(450)
                            isAutofocusingBeforeScan = false
                            onNavigateToCamera()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Collections, contentDescription = "Batch Scan", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("สแกนหลายหน้า (Batch)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Primary Document Scan Button
                ExtendedFloatingActionButton(
                    onClick = {
                        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        newFolderName = "Scan_${dateFormat.format(Date())}"
                        showNewFolderDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.AddAPhoto, "Scan icon") },
                    text = { Text("สแกนเอกสารใหม่", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(50),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (filteredDocs.isEmpty()) {
                // Distinctive Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPasteOff,
                        contentDescription = "Empty list",
                        modifier = Modifier
                            .size(90.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "ไม่พบเอกสารตามเงื่อนไข" else "ยังไม่มีการสแกนเอกสาร",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "ลองเริ่มค้นหาคำหลักอื่นหรือสร้างไฟล์ใหม่" 
                               else "กดปุ่มสแกนใหม่ เพื่อถ่ายภาพเอกสาร หนังสือ หรือใบเสร็จ แล้วใช้ AI แปลงเป็นหน้าแบนพร้อมทำ OCR ฟรี!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = LocalTextStyle.current.copy(lineHeight = 20.sp)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.updateSearchQuery("") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("ล้างตัวกรอง", color = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    newFolderName = "Scan_${dateFormat.format(Date())}"
                                    showNewFolderDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("สแกนเอกสารใหม่", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    galleryPicker.launch()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("นำเข้ารูปภาพ", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredDocs, key = { it.first.id }) { (doc, pages) ->
                            DocumentGridCard(
                                document = doc,
                                pages = pages,
                                isSelected = selectedDocIds.contains(doc.id),
                                isSelectionMode = selectedDocIds.isNotEmpty(),
                                onToggleSelect = {
                                    selectedDocIds = if (selectedDocIds.contains(doc.id)) {
                                        selectedDocIds - doc.id
                                    } else {
                                        selectedDocIds + doc.id
                                    }
                                },
                                onClick = {
                                    if (selectedDocIds.isNotEmpty()) {
                                        selectedDocIds = if (selectedDocIds.contains(doc.id)) {
                                            selectedDocIds - doc.id
                                        } else {
                                            selectedDocIds + doc.id
                                        }
                                    } else {
                                        onNavigateToPreview(doc)
                                    }
                                },
                                onExportPdf = {
                                    coroutineScope.launch {
                                        val pdfFile = ExportUtility.exportToPdf(context, doc, pages)
                                        if (pdfFile != null) {
                                            ExportUtility.shareFile(context, pdfFile, "application/pdf")
                                        }
                                    }
                                },
                                onRename = { showRenameDialog = doc },
                                onDelete = { showDeleteDialog = doc },
                                onMoveToFolder = { showMoveToFolderDoc = doc }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredDocs, key = { it.first.id }) { (doc, pages) ->
                            DocumentItemCard(
                                document = doc,
                                pages = pages,
                                isSelected = selectedDocIds.contains(doc.id),
                                isSelectionMode = selectedDocIds.isNotEmpty(),
                                onToggleSelect = {
                                    selectedDocIds = if (selectedDocIds.contains(doc.id)) {
                                        selectedDocIds - doc.id
                                    } else {
                                        selectedDocIds + doc.id
                                    }
                                },
                                onClick = {
                                    if (selectedDocIds.isNotEmpty()) {
                                        selectedDocIds = if (selectedDocIds.contains(doc.id)) {
                                            selectedDocIds - doc.id
                                        } else {
                                            selectedDocIds + doc.id
                                        }
                                    } else {
                                        onNavigateToPreview(doc)
                                    }
                                },
                                onExportPdf = {
                                    coroutineScope.launch {
                                        val pdfFile = ExportUtility.exportToPdf(context, doc, pages)
                                        if (pdfFile != null) {
                                            ExportUtility.shareFile(context, pdfFile, "application/pdf")
                                        }
                                    }
                                },
                                onRename = { showRenameDialog = doc },
                                onDelete = { showDeleteDialog = doc },
                                onMoveToFolder = { showMoveToFolderDoc = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog != null) {
        val doc = showRenameDialog!!
        var textValue by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("เปลี่ยนชื่อเอกสาร") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textValue.isNotBlank()) {
                        viewModel.renameDocument(doc, textValue)
                    }
                    showRenameDialog = null
                }) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteDialog != null) {
        val doc = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("ยืนยันลบเอกสาร?") },
            text = { Text("คุณกำลังจะลบเอกสาร '${doc.title}' พร้อมภาพสแกนทั้งหมดอย่างถาวร การดำเนินการนี้ไม่สามารถย้อนกลับได้") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ลบออก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var folderInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("สร้างโฟลเดอร์ใหม่", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("พิมพ์ชื่อโฟลเดอร์สำหรับจัดหมวดหมู่เอกสาร:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        placeholder = { Text("เช่น ใบเสร็จ & ภาษี, สัญญา...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("ชื่อแนะนำยอดนิยม:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val suggestions = listOf("ใบเสร็จ & บิล", "สัญญา & เอกสารสำคัญ", "เอกสารส่วนบุคคล", "การศึกษา", "ประกัน & การแพทย์")
                        items(suggestions) { suggestion ->
                            SuggestionChip(
                                onClick = { folderInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = folderInput.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.addCustomFolder(trimmed)
                            viewModel.setSelectedFolder(trimmed)
                        }
                        showCreateFolderDialog = false
                    },
                    enabled = folderInput.isNotBlank()
                ) {
                    Text("สร้างโฟลเดอร์")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Folder Management Dialog
    if (showFolderManagementDialog) {
        AlertDialog(
            onDismissRequest = { showFolderManagementDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("จัดการโฟลเดอร์", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text("รายการโฟลเดอร์ทั้งหมดในคลังเอกสาร (ระบบซิงก์ Cloud Firestore อัตโนมัติ):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ทั่วไป (โฟลเดอร์หลัก)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${allDocuments.count { it.folder == "ทั่วไป" }} เอกสาร", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        items(customFolders.filter { it != "ทั่วไป" }) { folderName ->
                            val docCount = allDocuments.count { it.folder == folderName }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(folderName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("$docCount เอกสาร", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteCustomFolder(folderName) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete folder",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFolderManagementDialog = false }) {
                    Text("เสร็จสิ้น")
                }
            }
        )
    }

    // Single Document Move to Folder Dialog
    if (showMoveToFolderDoc != null) {
        val targetDoc = showMoveToFolderDoc!!
        AlertDialog(
            onDismissRequest = { showMoveToFolderDoc = null },
            icon = {
                Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = { Text("ย้ายเอกสารไปโฟลเดอร์", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("เลือกโฟลเดอร์ปลายทางสำหรับ '${targetDoc.title}':", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val availableFolders = (listOf("ทั่วไป") + customFolders).distinct()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableFolders) { folderOption ->
                            val isCurrent = targetDoc.folder == folderOption
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.moveDocumentToFolder(targetDoc, folderOption)
                                        showMoveToFolderDoc = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folderOption,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Text("(โฟลเดอร์ปัจจุบัน)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = {
                            showMoveToFolderDoc = null
                            showCreateFolderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ สร้างโฟลเดอร์ใหม่...")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToFolderDoc = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Batch Move to Folder Dialog
    if (showBatchMoveDialog) {
        AlertDialog(
            onDismissRequest = { showBatchMoveDialog = false },
            icon = {
                Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = { Text("ย้าย ${selectedDocIds.size} รายการไปโฟลเดอร์", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("เลือกโฟลเดอร์ปลายทางสำหรับเอกสารที่เลือกทั้งหมด:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    val availableFolders = (listOf("ทั่วไป") + customFolders).distinct()
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableFolders) { folderOption ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.moveBatchDocumentsToFolder(selectedDocIds.toList(), folderOption)
                                        selectedDocIds = emptySet()
                                        showBatchMoveDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folderOption,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val count = allDocuments.count { it.folder == folderOption }
                                    Text("$count เอกสาร", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedButton(
                        onClick = {
                            showBatchMoveDialog = false
                            showCreateFolderDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("+ สร้างโฟลเดอร์ใหม่...")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchMoveDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("สร้างโฟลเดอร์เอกสารใหม่") },
            text = {
                Column {
                    Text("ตั้งชื่อโฟลเดอร์สำหรับเอกสารที่จะสแกนชุดนี้", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Pre-Scan Settings Reminder Card
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showNewFolderDialog = false
                                onNavigateToSettings()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "การตั้งค่าก่อนสแกนที่บันทึกไว้",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val filterName = when (preScanFilterMode) {
                                    "MAGIC_COLOR" -> "สีคมชัด"
                                    "ORIGINAL" -> "ต้นฉบับ"
                                    "BLACK_WHITE" -> "ขาว-ดำ"
                                    else -> "สีเทา"
                                }
                                Text(
                                    text = "ฟิลเตอร์: $filterName • PDF: ${if (preScanAutoGeneratePdf) "สร้าง" else "ไม่สร้าง"} • OCR: ${if (preScanRunOcr) "ถอดคำ" else "ปิด"}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = "ปรับค่า >",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Batch scan mode option row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setBatchScanningMode(!isBatchScanningMode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = "Batch Mode Icon",
                            tint = if (isBatchScanningMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "สแกนหลายหน้าเป็นชุด (Batch Mode)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBatchScanningMode) "เปิด: ถ่ายและรวมหน้าสแกนต่อเนื่องไม่จำกัดหน้า" else "ปิด: บันทึกหน้าเดียวแบบเร็ว",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBatchScanningMode,
                            onCheckedChange = { viewModel.setBatchScanningMode(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showNewFolderDialog = false
                            galleryPicker.launch()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("หรือเลือกนำเข้ารูปภาพจากคลัง (Gallery)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showNewFolderDialog = false
                    isAutofocusingBeforeScan = true
                    
                    coroutineScope.launch {
                        delay(1500)
                        isAutofocusingBeforeScan = false
                        
                        val finalName = newFolderName.trim().ifEmpty { "Untitled Document" }
                        viewModel.createNewDocument(finalName) {
                            onNavigateToCamera()
                        }
                    }
                }) {
                    Text("เริ่มสแกนด้วยกล้อง")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Image Import Confirmation & Configuration Dialog
    if (showImportConfigDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfigDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "นำเข้ารูปภาพเข้าสู่เอกสาร",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Summary Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "เลือกรูปภาพไว้ ${pendingImportUris.size} รูป",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Document Title Field
                    Column {
                        Text("ชื่อชุดเอกสาร", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = importDocTitle,
                            onValueChange = { importDocTitle = it },
                            placeholder = { Text("ตั้งชื่อเอกสาร...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Target Folder Selection
                    Column {
                        Text("บันทึกลงโฟลเดอร์", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val folders = listOf("ทั่วไป") + customFolders
                            items(folders) { fName ->
                                FilterChip(
                                    selected = importTargetFolder == fName,
                                    onClick = { importTargetFolder = fName },
                                    label = { Text(fName, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }

                    // Book Double-Page Split Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { importIsBookMode = !importIsBookMode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = if (importIsBookMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "โหมดหนังสือ 2 หน้า (แยกหน้าซ้าย-ขวา)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (importIsBookMode) "เปิด: ตัดแบ่งภาพหน้าคู่เป็น 2 หน้าอัตโนมัติ" else "ปิด: นำเข้า 1 รูป = 1 หน้า",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = importIsBookMode,
                            onCheckedChange = { importIsBookMode = it }
                        )
                    }

                    // Automatic Dewarp & Edge Detection indicator
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ระบบจะตรวจจับขอบเอกสาร ปรับมุมมองแบนเรียบ และปรับความคมชัดให้อัตโนมัติ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfigDialog = false
                        viewModel.addExternalPages(
                            imageUris = pendingImportUris,
                            customTitle = importDocTitle,
                            customFolder = importTargetFolder,
                            isBookMode = importIsBookMode
                        ) { doc ->
                            onNavigateToPreview(doc)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("นำเข้าและสร้างเอกสาร")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfigDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "ตั้งค่าการสแกนเอกสาร",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp) // Maintain compact dialog height
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "เลือกฟังก์ชันที่ต้องการให้ระบบประมวลผลโดยอัตโนมัติทันทีหลังสแกนเอกสาร",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 1: Auto Finger Removal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAutoFingerRemovalEnabled(!autoFingerRemovalEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Auto Finger Removal Icon",
                            tint = if (autoFingerRemovalEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ลบลายนิ้วมือขอบภาพอัตโนมัติ",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ระบบจะตรวจจับสีผิวบริเวณขอบเอกสาร และใช้ OpenCV ในการลบนิ้วมือออกโดยอัตโนมัติ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoFingerRemovalEnabled,
                            onCheckedChange = { viewModel.setAutoFingerRemovalEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 2: Auto De-warp / Flattening
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAutoDewarpEnabled(!autoDewarpEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Auto Flattening Icon",
                            tint = if (autoDewarpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ซ่อมแซมภาพปรับแนวตรงอัตโนมัติ",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "แก้ไขสัดส่วนและจัดรูปเอกสารให้เรียบตรง ไม่โค้งงอโดยอัตโนมัติ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoDewarpEnabled,
                            onCheckedChange = { viewModel.setAutoDewarpEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 6: Batch Scanning Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setBatchScanningMode(!isBatchScanningMode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = "Batch Mode Icon",
                            tint = if (isBatchScanningMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "โหมดสแกนหลายหน้าเป็นชุด (Batch)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "อนุญาตให้บันทึกภาพถ่ายต่อเนื่องหลายหน้า เพื่อประมวลผลและนำส่งออกรวมไฟล์พร้อมกัน",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBatchScanningMode,
                            onCheckedChange = { viewModel.setBatchScanningMode(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 3: Dark Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDarkModeEnabled(!darkModeEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (darkModeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Dark Mode Icon",
                            tint = if (darkModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "โหมดมืด (Dark Mode)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "เปลี่ยนธีมแอปพลิเคชันเป็นโทนสีมืดเพื่อถนอมสายตาขณะใช้งาน",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = darkModeEnabled,
                            onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 4: Scan Resolution (300 vs 600 DPI)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setScanResolutionHigh(!scanResolutionHigh) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = "Resolution Icon",
                            tint = if (scanResolutionHigh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ความละเอียดสูง 600 DPI",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "เปิดเพื่อภาพชัดสูงสุด 600 DPI (ปิดเพื่อความคมชัด 300 DPI ประหยัดพื้นที่โทรศัพท์)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = scanResolutionHigh,
                            onCheckedChange = { viewModel.setScanResolutionHigh(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Option 5: Storage Space Cleanup
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Storage Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "เพิ่มความจุโทรศัพท์",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
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
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Status Info",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = savingStorageStatus!!,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Option 6: Firebase Cloud Backup & Sync
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Cloud Sync Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "สำรองและกู้คืนข้อมูล (Firebase Firestore)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ซิงค์คอลเลกชันเอกสาร ข้อความ OCR และการจัดลำดับหน้าผ่าน Cloud",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.backupToCloud() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("สำรองข้อมูล", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.restoreFromCloud() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("กู้คืนข้อมูล", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            when (val status = syncStatus) {
                                is com.example.data.SyncStatus.Syncing -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("กำลังเชื่อมต่อและประมวลผลบน Firebase Cloud...", fontSize = 11.sp)
                                    }
                                }
                                is com.example.data.SyncStatus.Success -> {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = status.message,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                is com.example.data.SyncStatus.Error -> {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = status.message,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("ตกลง")
                }
            }
        )
    }

    // Cloud Firestore Sync Dialog
    if (showCloudSyncDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSyncDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "คลาวด์ซิงค์ (Firebase Firestore)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ซิงค์คลังเอกสาร ข้อความ OCR และภาพสแกนขึ้น Google Cloud เพื่อเปิดใช้งานข้ามอุปกรณ์ได้ทุกที่",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Stats & Status Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ซิงค์ล่าสุด:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = syncSummary.lastSyncTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "จำนวนเอกสารในเครื่อง:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${filteredDocs.size} ชุด",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Auto Sync Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAutoSyncEnabled(!syncSummary.autoSyncEnabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Auto Sync Icon",
                            tint = if (syncSummary.autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ซิงค์อัตโนมัติ (Auto-Sync)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "สำรองและอัปเดตข้อมูลขึ้น Cloud ทันทีที่มีการสแกนหรือแก้ไข",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = syncSummary.autoSyncEnabled,
                            onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Sync Action Buttons
                    Text(
                        text = "การจัดการซิงค์ข้อมูล",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Button 1: Full 2-Way Sync
                    Button(
                        onClick = { viewModel.fullSyncWithCloud() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ซิงค์คลังเอกสารทั้งหมด (Full Sync)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Buttons Row: Backup & Restore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.backupToCloud() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("สำรองขึ้น Cloud", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreFromCloud() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ดึงจาก Cloud", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Live Status Feedback
                    when (val status = syncStatus) {
                        is com.example.data.SyncStatus.Syncing -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = status.message,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        is com.example.data.SyncStatus.Success -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        is com.example.data.SyncStatus.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = status.message,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCloudSyncDialog = false }
                ) {
                    Text("ปิด")
                }
            }
        )
    }

    // Autofocusing Overlay Animation Dialog
    if (isAutofocusingBeforeScan) {
        Dialog(
            onDismissRequest = {} // Prevent dismissal during focus correction
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Pulsing Autofocus Reticle / Ring
                    val infiniteTransition = rememberInfiniteTransition(label = "Autofocus Pulse")
                    val animScale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .drawBehind {
                                // Draw a camera autofocus bracket/reticle
                                val sizePx = size.width
                                val thickness = 3.dp.toPx()
                                val cornerLength = 20.dp.toPx()
                                
                                scale(scale = animScale, pivot = androidx.compose.ui.geometry.Offset(sizePx / 2f, sizePx / 2f)) {
                                    // Top-Left
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(cornerLength, 0f),
                                        strokeWidth = thickness
                                    )
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                        end = androidx.compose.ui.geometry.Offset(0f, cornerLength),
                                        strokeWidth = thickness
                                    )

                                    // Top-Right
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(sizePx, 0f),
                                        end = androidx.compose.ui.geometry.Offset(sizePx - cornerLength, 0f),
                                        strokeWidth = thickness
                                    )
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(sizePx, 0f),
                                        end = androidx.compose.ui.geometry.Offset(sizePx, cornerLength),
                                        strokeWidth = thickness
                                    )

                                    // Bottom-Left
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(0f, sizePx),
                                        end = androidx.compose.ui.geometry.Offset(cornerLength, sizePx),
                                        strokeWidth = thickness
                                    )
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(0f, sizePx),
                                        end = androidx.compose.ui.geometry.Offset(0f, sizePx - cornerLength),
                                        strokeWidth = thickness
                                    )

                                    // Bottom-Right
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(sizePx, sizePx),
                                        end = androidx.compose.ui.geometry.Offset(sizePx - cornerLength, sizePx),
                                        strokeWidth = thickness
                                    )
                                    drawLine(
                                        color = Color.Green,
                                        start = androidx.compose.ui.geometry.Offset(sizePx, sizePx),
                                        end = androidx.compose.ui.geometry.Offset(sizePx, sizePx - cornerLength),
                                        strokeWidth = thickness
                                    )
                                    
                                    // Center dot
                                    drawCircle(
                                        color = Color.Green,
                                        radius = 4.dp.toPx()
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = "Autofocusing",
                            tint = Color.Green,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "กำลังล็อคออโต้โฟกัส...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "ปรับระยะชัดลึกและชดเชยแสงเพื่อภาพคมชัดที่สุด (${if (scanResolutionHigh) "600" else "300"} DPI)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }

    // Batch Processing Progress Dialog
    if (isProcessingBatch) {
        Dialog(
            onDismissRequest = {} // Prevent dismissal during processing
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (batchTotal > 0) batchProgress.toFloat() / batchTotal.toFloat() else 0f },
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "กำลังประมวลผลกลุ่มหน้าสแกน...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = batchStatusText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "ลบเงา ปรับระนาบหน้าแบน และชดเชยรายละเอียดโดยอัตโนมัติ",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }

    // Batch PDF Created Success Dialog
    if (batchResult != null) {
        val result = batchResult!!
        val doc = result.document
        val pdfFile = result.pdfFile
        val pageCount = result.pageCount
        val fileSizeText = if (pdfFile != null && pdfFile.exists()) {
            val bytes = pdfFile.length()
            if (bytes > 1024 * 1024) String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
            else String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        } else "N/A"

        AlertDialog(
            onDismissRequest = { viewModel.clearBatchResult() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    text = "สร้างไฟล์ PDF หลายหน้าสำเร็จ!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = doc.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("จำนวนหน้า: $pageCount หน้า", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("ขนาดไฟล์: $fileSizeText", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                            if (result.generatedTime.isNotEmpty()) {
                                Text("บันทึกเมื่อ: ${result.generatedTime}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }

                    if (pdfFile != null && pdfFile.exists()) {
                        Button(
                            onClick = {
                                ExportUtility.openPdfFile(context, pdfFile)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📄 เปิดอ่านไฟล์ PDF ทันที", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                ExportUtility.shareFile(context, pdfFile, "application/pdf")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📤 แชร์ไฟล์ PDF ไปยังแอปอื่น", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDoc = doc
                        viewModel.clearBatchResult()
                        onNavigateToPreview(targetDoc)
                    }
                ) {
                    Text("ดูในคลังเอกสาร")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearBatchResult() }) {
                    Text("ปิด")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItemCard(
    document: Document,
    pages: List<ScannedPage>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateString = remember(document.createdTime) {
        val date = Date(document.createdTime)
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(date)
    }
    
    // Loaded thumbnail from first page processed image or fallback
    val firstPage = pages.firstOrNull()
    val thumbnailBitmap = remember(firstPage?.processedImagePath) {
        if (firstPage != null) {
            val file = File(firstPage.processedImagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } else null
    }

    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onToggleSelect != null) {
                        onToggleSelect()
                    } else {
                        expandedMenu = true
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect?.invoke() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // First page image thumbnail with realistic scanner framing
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Doc icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Page count overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(topStart = 6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${pages.size} หน้า",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info Panel
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRename() }
                ) {
                    Text(
                        text = document.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Title",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(3.dp))

                // Folder badge
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = document.folder,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(3.dp))
                
                Text(
                    text = "สร้างเมื่อ: $dateString",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Feature tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pages.any { it.ocrText.isNotEmpty() && it.ocrText != "Pending OCR Process..." && !it.ocrText.startsWith("OCR Request Failed") }) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("OCR สำเร็จ", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = null,
                            modifier = Modifier.height(20.dp)
                        )
                    }

                    if (pages.any { it.hasDeWarp }) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("ลบโค้งกระดาษ", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                labelColor = MaterialTheme.colorScheme.secondary
                            ),
                            border = null,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }

            // Options Button
            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options menu",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("เปิดดูเอกสาร") },
                        leadingIcon = { Icon(Icons.Default.Launch, contentDescription = "Open") },
                        onClick = {
                            expandedMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ย้ายไปโฟลเดอร์...") },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = "Move folder", tint = MaterialTheme.colorScheme.secondary) },
                        onClick = {
                            expandedMenu = false
                            onMoveToFolder()
                        }
                    )
                    if (onExportPdf != null) {
                        DropdownMenuItem(
                            text = { Text("ส่งออก PDF & แชร์") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                expandedMenu = false
                                onExportPdf()
                            }
                        )
                    }
                    if (onToggleSelect != null) {
                        DropdownMenuItem(
                            text = { Text(if (isSelected) "ยกเลิกการเลือก" else "เลือกรายการนี้") },
                            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = "Select") },
                            onClick = {
                                expandedMenu = false
                                onToggleSelect()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("เปลี่ยนชื่อไฟล์") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit") },
                        onClick = {
                            expandedMenu = false
                            onRename()
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("ลบเอกสาร", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridCard(
    document: Document,
    pages: List<ScannedPage>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onExportPdf: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateString = remember(document.createdTime) {
        val date = Date(document.createdTime)
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }
    
    val firstPage = pages.firstOrNull()
    val thumbnailBitmap = remember(firstPage?.processedImagePath) {
        if (firstPage != null) {
            val file = File(firstPage.processedImagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } else null
    }

    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onToggleSelect != null) {
                        onToggleSelect()
                    } else {
                        expandedMenu = true
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail Container for Gallery view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Thumbnail for ${document.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Collection icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect?.invoke() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    )
                }

                // Page Count Overlay Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "${pages.size} หน้า",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Info & Menu Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRename() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = document.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Title",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { expandedMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options menu",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("เปิดดูเอกสาร") },
                                leadingIcon = { Icon(Icons.Default.Launch, contentDescription = "Open") },
                                onClick = {
                                    expandedMenu = false
                                    onClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ย้ายไปโฟลเดอร์...") },
                                leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = "Move folder", tint = MaterialTheme.colorScheme.secondary) },
                                onClick = {
                                    expandedMenu = false
                                    onMoveToFolder()
                                }
                            )
                            if (onExportPdf != null) {
                                DropdownMenuItem(
                                    text = { Text("ส่งออก PDF & แชร์") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        expandedMenu = false
                                        onExportPdf()
                                    }
                                )
                            }
                            if (onToggleSelect != null) {
                                DropdownMenuItem(
                                    text = { Text(if (isSelected) "ยกเลิกการเลือก" else "เลือกรายการนี้") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = "Select") },
                                    onClick = {
                                        expandedMenu = false
                                        onToggleSelect()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("เปลี่ยนชื่อไฟล์") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit") },
                                onClick = {
                                    expandedMenu = false
                                    onRename()
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("ลบเอกสาร", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    expandedMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Folder tag in Grid card
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = document.folder,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = dateString,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
