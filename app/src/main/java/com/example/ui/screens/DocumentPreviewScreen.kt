package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Document
import com.example.data.ScannedPage
import com.example.ui.ScannerViewModel
import com.example.ui.components.ZoomableImage
import com.example.ui.components.rememberGalleryPickerLauncher
import com.example.util.ExportUtility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWorkspace: (ScannedPage) -> Unit,
    onNavigateToOcrPanel: (ScannedPage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val document by viewModel.currentDocument.collectAsState()
    val pages by viewModel.currentPages.collectAsState()
    val isRtl by viewModel.isRtlFlow.collectAsState()

    var showExportMenu by remember { mutableStateOf(false) }
    var isReorderMode by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    val customFolders by viewModel.customFolders.collectAsState()
    var inspectZoomPage by remember { mutableStateOf<ScannedPage?>(null) }
    var reorderedPagesList by remember(pages, isReorderMode) { mutableStateOf(pages) }

    val galleryPicker = rememberGalleryPickerLauncher { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addExternalPages(uris)
        }
    }

    if (document == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        if (isReorderMode) {
                            Text(
                                text = "จัดเรียงหน้าใหม่",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "กดค้างแล้วลากเพื่อจัดลำดับหน้า (หรือใช้ปุ่มลูกศร)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showRenameDialog = true }
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = document!!.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable { showMoveFolderDialog = true }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = document!!.folder,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${pages.size} หน้าบันทึก" + if (isRtl) " (RTL)" else "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Collection Title",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isReorderMode) {
                        IconButton(onClick = { isReorderMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = "ยกเลิก")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isReorderMode) {
                        IconButton(
                            onClick = {
                                viewModel.reorderPages(reorderedPagesList)
                                isReorderMode = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "บันทึกการจัดเรียงหน้า",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            galleryPicker.launch()
                        }) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "นำเข้ารูปภาพเพิ่มในเอกสารนี้",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showMoveFolderDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "ย้ายโฟลเดอร์",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = { viewModel.setRtlOrder(!isRtl) }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Toggle RTL Scan",
                                tint = if (isRtl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isReorderMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "จัดเรียงหน้าใหม่",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.IosShare, contentDescription = "Export Document")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (pages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "Empty pages",
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "ยังไม่มีหน้าเอกสารในเอกสารนี้",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "กลับไปสแกน หรือเลือกนำเข้ารูปภาพจากเครื่องเข้าเอกสารนี้ได้ทันที",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            galleryPicker.launch()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("นำเข้ารูปภาพจากคลัง (Gallery)", fontSize = 14.sp)
                    }
                }
            } else if (isReorderMode) {
                ReorderPagesContainer(
                    pages = reorderedPagesList,
                    onOrderChanged = { reorderedPagesList = it }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    
                    // Document Rename Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            var docTitle by remember(document?.title) { mutableStateOf(document?.title ?: "") }
                            
                            OutlinedTextField(
                                value = docTitle,
                                onValueChange = { newValue ->
                                    docTitle = newValue
                                    viewModel.renameDocument(document!!, newValue)
                                },
                                label = { Text("ชื่อเอกสารสแกน (Rename Document)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("document_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Main Pages Grid View
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(pages, key = { it.id }) { page ->
                            PagePreviewCard(
                                page = page,
                                onClick = { onNavigateToWorkspace(page) },
                                onOcrClick = { onNavigateToOcrPanel(page) },
                                onDelete = { viewModel.deletePage(page) },
                                onInspectClick = { inspectZoomPage = page }
                            )
                        }
                    }

                    // Lower Quick Action Panel
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "พร้อมสำหรับส่งออก:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            val coroutineScope = rememberCoroutineScope()
                            Button(
                                onClick = { 
                                    coroutineScope.launch {
                                        val file = ExportUtility.exportToPdf(context, document!!, pages)
                                        if (file != null) {
                                            ExportUtility.shareFile(context, file, "application/pdf")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF icon", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ส่งออก PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Collection Title Dialog
    if (showRenameDialog && document != null) {
        var newTitle by remember { mutableStateOf(document!!.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("เปลี่ยนชื่อชุดเอกสาร", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("ชื่อเอกสารใหม่") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.renameDocument(document!!, newTitle.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Move to Folder Dialog in Preview Screen
    if (showMoveFolderDialog && document != null) {
        val currentDoc = document!!
        var newFolderInput by remember { mutableStateOf("") }
        var showCreateInline by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showMoveFolderDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DriveFileMove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("ย้ายไปโฟลเดอร์", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("เลือกโฟลเดอร์จัดเก็บสำหรับ '${currentDoc.title}':", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val availableFolders = (listOf("ทั่วไป") + customFolders).distinct()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        availableFolders.forEach { folderOption ->
                            val isCurrent = currentDoc.folder == folderOption
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.moveDocumentToFolder(currentDoc, folderOption)
                                        showMoveFolderDialog = false
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
                                        Text("(ปัจจุบัน)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (showCreateInline) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newFolderInput,
                                onValueChange = { newFolderInput = it },
                                label = { Text("ชื่อโฟลเดอร์ใหม่") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val trimmed = newFolderInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.addCustomFolder(trimmed)
                                        viewModel.moveDocumentToFolder(currentDoc, trimmed)
                                    }
                                    showMoveFolderDialog = false
                                },
                                enabled = newFolderInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("บันทึกและย้ายทันที")
                            }
                        } else {
                            TextButton(
                                onClick = { showCreateInline = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ สร้างโฟลเดอร์ใหม่")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveFolderDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    // Export Formats Bottom Sheet / Dialog Choice
    if (showExportMenu) {
        val coroutineScope = rememberCoroutineScope()
        var exportFileName by remember { mutableStateOf(document?.title ?: "Document") }
        
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("ตั้งชื่อไฟล์และส่งออก", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = exportFileName,
                        onValueChange = { exportFileName = it },
                        label = { Text("ชื่อไฟล์") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    
                    ExportMenuOption(
                        title = "ส่งออกเป็นไฟล์ PDF",
                        sub = "จัดหน้าและภาพสแกนแบบ High-Definition ไหลลื่น",
                        icon = Icons.Default.PictureAsPdf,
                        color = Color(0xFFE57373),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.renameDocument(document!!, exportFileName)
                                val file = ExportUtility.exportToPdf(context, document!!.copy(title = exportFileName), pages, exportFileName)
                                if (file != null) {
                                    ExportUtility.shareFile(context, file, "application/pdf")
                                }
                            }
                            showExportMenu = false
                        }
                    )
                    ExportMenuOption(
                        title = "ส่งออกเป็นข้อความดิบ (TEXT TXT)",
                        sub = "ดึงเฉพาะข้อความดิบทั้งหมดที่แกะจากภาพสแกน",
                        icon = Icons.Default.TextFields,
                        color = Color(0xFF64B5F6),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.renameDocument(document!!, exportFileName)
                                val file = ExportUtility.exportToTxt(context, document!!.copy(title = exportFileName), pages, exportFileName)
                                if (file != null) {
                                    ExportUtility.shareFile(context, file, "text/plain")
                                }
                            }
                            showExportMenu = false
                        }
                    )
                    ExportMenuOption(
                        title = "ส่งออกเป็นไฟล์เอกสาร Word (DOC)",
                        sub = "บันทึกในรูปแบบ Rich Document แท็ก HTML",
                        icon = Icons.Default.Description,
                        color = Color(0xFF81C784),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.renameDocument(document!!, exportFileName)
                                val file = ExportUtility.exportToWord(context, document!!.copy(title = exportFileName), pages, exportFileName)
                                if (file != null) {
                                    ExportUtility.shareFile(context, file, "application/msword")
                                }
                            }
                            showExportMenu = false
                        }
                    )
                    ExportMenuOption(
                        title = "สร้างลิงค์แชร์ดูผ่านเว็บเบราว์เซอร์",
                        sub = "ก๊อปปี้ Link ลงคลิปบอร์ดส่งให้เพื่อนเปิดได้ทุกอุปกรณ์ทันที",
                        icon = Icons.Default.Share,
                        color = Color(0xFFFFB74D),
                        onClick = {
                            ExportUtility.shareWebLink(context, document!!)
                            showExportMenu = false
                        }
                    )
                    ExportMenuOption(
                        title = "สำรองเอกสารนี้ขึ้น Cloud Firestore",
                        sub = "ซิงค์ภาพและข้อความทั้งหมดของเอกสารนี้เพื่อเปิดใช้งานบนอุปกรณ์อื่น",
                        icon = Icons.Default.CloudUpload,
                        color = Color(0xFF42A5F5),
                        onClick = {
                            viewModel.backupSingleDocument(document!!.id)
                            showExportMenu = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportMenu = false }) {
                    Text("ปิด")
                }
            }
        )
    }

    // Fullscreen Zoomable Image Inspection Dialog
    if (inspectZoomPage != null) {
        val inspectBitmap = remember(inspectZoomPage!!.processedImagePath) {
            val file = File(inspectZoomPage!!.processedImagePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }

        Dialog(
            onDismissRequest = { inspectZoomPage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (inspectBitmap != null) {
                        ZoomableImage(
                            bitmap = inspectBitmap,
                            contentDescription = "Inspect page ${inspectZoomPage!!.pageNumber}",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }

                    // Top Bar overlay header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Inspect Icon",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ตรวจสอบหน้า ${inspectZoomPage!!.pageNumber} (ใช้นิ้วซูมเข้า-ออก)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        IconButton(
                            onClick = { inspectZoomPage = null },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close inspect", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PagePreviewCard(
    page: ScannedPage,
    onClick: () -> Unit,
    onOcrClick: () -> Unit,
    onDelete: () -> Unit,
    onInspectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(page.processedImagePath) {
        val file = File(page.processedImagePath)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Visual page image container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page screen",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.BrokenImage, "Broken", tint = Color.Gray)
                    }
                }

                // Page number sticker badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "หน้า ${page.pageNumber}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Top End Action Buttons Overlay (Zoom Inspect & Delete)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onInspectClick != null) {
                        IconButton(
                            onClick = onInspectClick,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(30.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom Inspect", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(30.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Text / Enhancements descriptions underneath
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (page.filterMode == "ORIGINAL") "ภาพต้นฉบับ" else "ฟิลเตอร์: ${page.filterMode}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // OCR Status info / click to trigger TTS readings
                Button(
                    onClick = onOcrClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (page.ocrText.isNotEmpty() && page.ocrText != "Pending OCR Process...") 
                                            MaterialTheme.colorScheme.primary 
                                         else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (page.ocrText.isNotEmpty() && page.ocrText != "Pending OCR Process...") 
                                         Icons.Default.RecordVoiceOver 
                                      else Icons.Default.DocumentScanner,
                        contentDescription = "OCR icon",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (page.ocrText.isNotEmpty() && page.ocrText != "Pending OCR Process...") 
                                   "เปิดเครื่องอ่านฟรี (TTS)" 
                               else "ทำความจำอักษร (OCR)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ExportMenuOption(
    title: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = color)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(sub, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun ReorderPagesContainer(
    pages: List<ScannedPage>,
    onOrderChanged: (List<ScannedPage>) -> Unit,
    modifier: Modifier = Modifier
) {
    var mutablePagesList by remember(pages) { mutableStateOf(pages.toList()) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    var pageToMoveIndex by remember { mutableStateOf<Int?>(null) } // Target page index for index-based move dialog

    // Dialog for index-based direct reordering
    if (pageToMoveIndex != null) {
        val currentIndex = pageToMoveIndex!!
        var targetPositionText by remember { mutableStateOf("${currentIndex + 1}") }

        AlertDialog(
            onDismissRequest = { pageToMoveIndex = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("ย้ายหน้าไปลำดับที่กำหนด", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "หน้าที่ ${currentIndex + 1} จากทั้งหมด ${mutablePagesList.size} หน้า",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                                onOrderChanged(newList)
                            }
                        }
                        pageToMoveIndex = null
                    }
                ) {
                    Text("ย้ายลำดับ")
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToMoveIndex = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Action Tools Bar for Batch Reordering
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "รวม ${mutablePagesList.size} หน้า",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val reversed = mutablePagesList.reversed()
                            mutablePagesList = reversed
                            onOrderChanged(reversed)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("กลับลำดับทั้งหมด", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            val reset = pages.sortedBy { it.pageNumber }
                            mutablePagesList = reset
                            onOrderChanged(reset)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("คืนค่าเดิม", fontSize = 11.sp)
                    }
                }
            }
        }

        mutablePagesList.forEachIndexed { index, page ->
            val isDragging = draggingIndex == index
            val scale = if (isDragging) 1.05f else 1.0f
            val elevation = if (isDragging) 8.dp else 2.dp
            
            // Calculate translation for the currently dragged item
            val translationY = if (isDragging) draggedDistance else 0f

            val bitmap = remember(page.processedImagePath) {
                val file = File(page.processedImagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        this.translationY = translationY
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                    .pointerInput(mutablePagesList) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggingIndex = index
                                draggedDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedDistance += dragAmount.y
                                
                                val currentDragIndex = draggingIndex
                                if (currentDragIndex != null) {
                                    val itemHeightPx = 110.dp.toPx() // Estimated height of item in pixels including padding
                                    val hoverOffsetIndex = (draggedDistance / itemHeightPx).toInt()
                                    val targetIndex = currentDragIndex + hoverOffsetIndex
                                    if (targetIndex in mutablePagesList.indices && targetIndex != currentDragIndex) {
                                        val newList = mutablePagesList.toMutableList()
                                        val movedItem = newList.removeAt(currentDragIndex)
                                        newList.add(targetIndex, movedItem)
                                        mutablePagesList = newList
                                        draggingIndex = targetIndex
                                        draggedDistance = 0f
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                draggedDistance = 0f
                                onOrderChanged(mutablePagesList)
                            },
                            onDragCancel = {
                                draggingIndex = null
                                draggedDistance = 0f
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag Handle Icon
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "ลากเพื่อย้ายหน้า",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                    )

                    // Page Image Preview
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = "Broken",
                                modifier = Modifier.align(Alignment.Center),
                                tint = Color.Gray
                            )
                        }

                        // Mini badge for current position
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Page details & Index Reorder Button
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "หน้าที่ ${index + 1} (เดิมหน้า ${page.pageNumber})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Index-Based Reorder Button
                        AssistChip(
                            onClick = { pageToMoveIndex = index },
                            label = { Text("ย้ายไปหน้าที่...", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change Position Index",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }

                    // Up / Down Quick Actions (for precise fine reordering!)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val newList = mutablePagesList.toMutableList()
                                    val movedItem = newList.removeAt(index)
                                    newList.add(index - 1, movedItem)
                                    mutablePagesList = newList
                                    onOrderChanged(newList)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move page up",
                                tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                if (index < mutablePagesList.size - 1) {
                                    val newList = mutablePagesList.toMutableList()
                                    val movedItem = newList.removeAt(index)
                                    newList.add(index + 1, movedItem)
                                    mutablePagesList = newList
                                    onOrderChanged(newList)
                                }
                            },
                            enabled = index < mutablePagesList.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move page down",
                                tint = if (index < mutablePagesList.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
