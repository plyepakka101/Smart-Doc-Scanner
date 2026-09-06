package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.MlKitOcrService
import com.example.data.*
import com.example.util.ImageProcessor
import com.example.util.MockDocumentGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class BatchScanConfig(
    val title: String = "",
    val folder: String = "ทั่วไป",
    val filterMode: String = "ORIGINAL",
    val runOcr: Boolean = false,
    val autoGeneratePdf: Boolean = true
)

data class BatchResult(
    val document: Document,
    val pdfFile: File?,
    val pageCount: Int,
    val generatedTime: String = ""
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DocumentRepository(database.documentDao())
    private val prefs = application.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    val syncManager = FirestoreSyncManager(application, repository)
    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus
    val syncSummary: StateFlow<CloudSyncSummary> = syncManager.syncSummary

    private val _batchResultState = MutableStateFlow<BatchResult?>(null)
    val batchResultState: StateFlow<BatchResult?> = _batchResultState.asStateFlow()

    fun clearBatchResult() {
        _batchResultState.value = null
    }

    fun backupToCloud() {
        viewModelScope.launch {
            syncManager.backupAllToCloud()
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            syncManager.restoreFromCloud()
        }
    }

    fun fullSyncWithCloud() {
        viewModelScope.launch {
            syncManager.fullSync()
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        syncManager.setAutoSyncEnabled(enabled)
    }

    fun backupSingleDocument(docId: Long) {
        viewModelScope.launch {
            syncManager.backupSingleDocument(docId)
        }
    }

    // All saved documents
    val allDocuments: StateFlow<List<Document>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current selected document for preview / edit
    private val _currentDocument = MutableStateFlow<Document?>(null)
    val currentDocument: StateFlow<Document?> = _currentDocument.asStateFlow()

    // Pages of the current document
    private val _currentPages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val currentPages: StateFlow<List<ScannedPage>> = _currentPages.asStateFlow()

    // UI Scanning Mode states
    var isRtlFlow = MutableStateFlow(false)
    var isContinuousScanMode = MutableStateFlow(false)
    var continuousScanIntervalSeconds = MutableStateFlow(3) // auto scan timer

    // Image enhancements configurations
    var autoDewarpEnabled = MutableStateFlow(prefs.getBoolean("auto_dewarp_enabled", true))
    var autoFingerRemovalEnabled = MutableStateFlow(prefs.getBoolean("auto_finger_removal_enabled", true))
    var activeFilterMode = MutableStateFlow("ORIGINAL") // ORIGINAL, ENHANCED, BLACK_WHITE, GRAYSCALE

    // New Settings Requested
    var darkModeEnabled = MutableStateFlow(prefs.getBoolean("dark_mode_enabled", false))
    var scanResolutionHigh = MutableStateFlow(prefs.getBoolean("scan_resolution_high", false)) // false = 300dpi, true = 600dpi
    var savingStorageStatus = MutableStateFlow<String?>(null)

    // Pre-Scan Configuration & Defaults (Persisted until user explicitly changes them)
    var preScanFilterMode = MutableStateFlow(prefs.getString("pre_scan_filter_mode", "MAGIC_COLOR") ?: "MAGIC_COLOR")
    var preScanRunOcr = MutableStateFlow(prefs.getBoolean("pre_scan_run_ocr", false))
    var preScanAutoGeneratePdf = MutableStateFlow(prefs.getBoolean("pre_scan_auto_generate_pdf", true))
    var preScanDefaultFolder = MutableStateFlow(prefs.getString("pre_scan_default_folder", "ทั่วไป") ?: "ทั่วไป")
    var preScanScanMode = MutableStateFlow(prefs.getString("pre_scan_scan_mode", "BOOK_2PAGE") ?: "BOOK_2PAGE")
    var preScanAutoCapture = MutableStateFlow(prefs.getBoolean("pre_scan_auto_capture", false))
    var preScanAutoOrientation = MutableStateFlow(prefs.getBoolean("pre_scan_auto_orientation", true))
    var preScanCameraZoom = MutableStateFlow(prefs.getFloat("pre_scan_camera_zoom", 1.35f))
    var bookAspectRatio = MutableStateFlow(prefs.getFloat("book_aspect_ratio", 1.414f)) // 1.414f = standard A4/A5 book spread (1.41:1)
    var skipFinishConfirmDialog = MutableStateFlow(prefs.getBoolean("skip_finish_confirm_dialog", false))

    // Bluetooth Remote Shutter & External Camera App integration
    var bluetoothShutterEnabled = MutableStateFlow(prefs.getBoolean("bluetooth_shutter_enabled", true))
    var systemCameraShutterAlignment = MutableStateFlow(prefs.getBoolean("system_camera_shutter_alignment", true))
    var tapScreenToScan = MutableStateFlow(prefs.getBoolean("tap_screen_to_scan", false))
    var useSystemCameraApp = MutableStateFlow(prefs.getBoolean("use_system_camera_app", false))
    val remoteShutterEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToCameraEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var isCameraActive = MutableStateFlow(false)

    fun triggerRemoteShutter() {
        remoteShutterEvent.tryEmit(Unit)
    }

    fun requestNavigateToCamera() {
        navigateToCameraEvent.tryEmit(Unit)
    }

    fun setCameraActive(active: Boolean) {
        isCameraActive.value = active
    }

    fun setBluetoothShutterEnabled(enabled: Boolean) {
        bluetoothShutterEnabled.value = enabled
        prefs.edit().putBoolean("bluetooth_shutter_enabled", enabled).apply()
    }

    fun setSystemCameraShutterAlignment(enabled: Boolean) {
        systemCameraShutterAlignment.value = enabled
        prefs.edit().putBoolean("system_camera_shutter_alignment", enabled).apply()
    }

    fun setTapScreenToScan(enabled: Boolean) {
        tapScreenToScan.value = enabled
        prefs.edit().putBoolean("tap_screen_to_scan", enabled).apply()
    }

    fun setUseSystemCameraApp(enabled: Boolean) {
        useSystemCameraApp.value = enabled
        prefs.edit().putBoolean("use_system_camera_app", enabled).apply()
    }

    // Batch Scanning & Processing states
    var isBatchScanningMode = MutableStateFlow(prefs.getBoolean("is_batch_scanning_mode", true))
    var isProcessingBatch = MutableStateFlow(false)
    var batchProgress = MutableStateFlow(0)
    var batchTotal = MutableStateFlow(0)
    var batchStatusText = MutableStateFlow("")

    fun setPreScanFilterMode(mode: String) {
        preScanFilterMode.value = mode
        prefs.edit().putString("pre_scan_filter_mode", mode).apply()
    }

    fun setPreScanRunOcr(enabled: Boolean) {
        preScanRunOcr.value = enabled
        prefs.edit().putBoolean("pre_scan_run_ocr", enabled).apply()
    }

    fun setPreScanAutoGeneratePdf(enabled: Boolean) {
        preScanAutoGeneratePdf.value = enabled
        prefs.edit().putBoolean("pre_scan_auto_generate_pdf", enabled).apply()
    }

    fun setPreScanDefaultFolder(folder: String) {
        preScanDefaultFolder.value = folder
        prefs.edit().putString("pre_scan_default_folder", folder).apply()
    }

    fun setPreScanScanMode(mode: String) {
        preScanScanMode.value = mode
        prefs.edit().putString("pre_scan_scan_mode", mode).apply()
        if (mode == "SINGLE") {
            setBatchScanningMode(false)
        } else {
            setBatchScanningMode(true)
        }
    }

    fun setPreScanAutoCapture(enabled: Boolean) {
        preScanAutoCapture.value = enabled
        prefs.edit().putBoolean("pre_scan_auto_capture", enabled).apply()
    }

    fun setPreScanAutoOrientation(enabled: Boolean) {
        preScanAutoOrientation.value = enabled
        prefs.edit().putBoolean("pre_scan_auto_orientation", enabled).apply()
    }

    fun setPreScanCameraZoom(zoom: Float) {
        preScanCameraZoom.value = zoom
        prefs.edit().putFloat("pre_scan_camera_zoom", zoom).apply()
    }

    fun setBookAspectRatio(ratio: Float) {
        bookAspectRatio.value = ratio
        prefs.edit().putFloat("book_aspect_ratio", ratio).apply()
    }

    fun setSkipFinishConfirmDialog(skip: Boolean) {
        skipFinishConfirmDialog.value = skip
        prefs.edit().putBoolean("skip_finish_confirm_dialog", skip).apply()
    }

    fun getCurrentPreScanConfig(customTitle: String? = null): BatchScanConfig {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val title = if (!customTitle.isNullOrBlank()) customTitle else "Scan_${dateFormat.format(Date())}"
        return BatchScanConfig(
            title = title,
            folder = preScanDefaultFolder.value,
            filterMode = preScanFilterMode.value,
            runOcr = preScanRunOcr.value,
            autoGeneratePdf = preScanAutoGeneratePdf.value
        )
    }

    fun setBatchScanningMode(enabled: Boolean) {
        isBatchScanningMode.value = enabled
        prefs.edit().putBoolean("is_batch_scanning_mode", enabled).apply()
    }

    fun setAutoDewarpEnabled(enabled: Boolean) {
        autoDewarpEnabled.value = enabled
        prefs.edit().putBoolean("auto_dewarp_enabled", enabled).apply()
    }

    fun setAutoFingerRemovalEnabled(enabled: Boolean) {
        autoFingerRemovalEnabled.value = enabled
        prefs.edit().putBoolean("auto_finger_removal_enabled", enabled).apply()
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        darkModeEnabled.value = enabled
        prefs.edit().putBoolean("dark_mode_enabled", enabled).apply()
    }

    fun setScanResolutionHigh(high: Boolean) {
        scanResolutionHigh.value = high
        prefs.edit().putBoolean("scan_resolution_high", high).apply()
    }

    fun deleteRawImagesToSaveStorage(context: Context) {
        viewModelScope.launch {
            savingStorageStatus.value = "กำลังประมวลผล..."
            var bytesSaved: Long = 0
            var filesDeletedCount = 0
            
            withContext(Dispatchers.IO) {
                try {
                    val allPages = repository.getAllPages()
                    for (page in allPages) {
                        // If original file is different from processed file, we can delete the original
                        if (page.originalImagePath != page.processedImagePath) {
                            val origFile = File(page.originalImagePath)
                            if (origFile.exists()) {
                                val size = origFile.length()
                                val deleted = origFile.delete()
                                if (deleted) {
                                    bytesSaved += size
                                    filesDeletedCount++
                                    // Update database page so original path points to processed path
                                    val updatedPage = page.copy(originalImagePath = page.processedImagePath)
                                    repository.updatePage(updatedPage)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            val mbSaved = String.format("%.2f", bytesSaved.toDouble() / (1024.0 * 1024.0))
            if (filesDeletedCount > 0) {
                savingStorageStatus.value = "ลบแล้ว $filesDeletedCount ไฟล์ ประหยัดพื้นที่ได้ $mbSaved MB!"
            } else {
                savingStorageStatus.value = "ไม่พบไฟล์ภาพต้นฉบับให้ลบ (หรือลบไปแล้ว)"
            }
        }
    }

    // Active page editing crop coordinates overlay
    var activeCropCoordinates = MutableStateFlow<List<PointF>>(
        listOf(PointF(0.08f, 0.05f), PointF(0.92f, 0.05f), PointF(0.92f, 0.95f), PointF(0.08f, 0.95f))
    )

    // TTS configurations
    var ttsRate = MutableStateFlow(1.0f)
    var ttsPitch = MutableStateFlow(1.0f)

    // OCR Running state
    private val _ocrStatus = MutableStateFlow<OcrStatus>(OcrStatus.Idle)
    val ocrStatus: StateFlow<OcrStatus> = _ocrStatus.asStateFlow()

    // Search query in documents
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Custom user-defined folders state
    private val defaultFoldersList = listOf(
        "ทั่วไป",
        "ใบเสร็จ/บิล",
        "สัญญา/นิติกรรม",
        "เอกสารการทำงาน",
        "การเรียน/บทเรียน",
        "เอกสารส่วนตัว",
        "ใบกำกับภาษี"
    )

    private val _selectedFolder = MutableStateFlow<String>("ทั้งหมด")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _customFolders = MutableStateFlow<List<String>>(loadFoldersFromPrefs())
    val customFolders: StateFlow<List<String>> = _customFolders.asStateFlow()

    private fun loadFoldersFromPrefs(): List<String> {
        val saved = prefs.getStringSet("custom_user_folders_set", null)
        return if (saved.isNullOrEmpty()) {
            defaultFoldersList
        } else {
            val list = saved.toMutableList()
            if (!list.contains("ทั่วไป")) {
                list.add(0, "ทั่วไป")
            }
            list.distinct()
        }
    }

    private fun saveFoldersToPrefs(folders: List<String>) {
        prefs.edit().putStringSet("custom_user_folders_set", folders.toSet()).apply()
        _customFolders.value = folders
    }

    fun setSelectedFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun addCustomFolder(folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty() || trimmed == "ทั้งหมด") return
        val current = _customFolders.value.toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            saveFoldersToPrefs(current)
        }
        _selectedFolder.value = trimmed
    }

    fun renameCustomFolder(oldName: String, newName: String) {
        val trimmedNew = newName.trim()
        if (trimmedNew.isEmpty() || oldName == "ทั่วไป" || trimmedNew == "ทั้งหมด" || oldName == trimmedNew) return
        viewModelScope.launch {
            repository.renameFolderInDocuments(oldName, trimmedNew)
            val current = _customFolders.value.map { if (it == oldName) trimmedNew else it }.distinct()
            saveFoldersToPrefs(current)
            if (_selectedFolder.value == oldName) {
                _selectedFolder.value = trimmedNew
            }
        }
    }

    fun deleteCustomFolder(folderName: String, moveDocsToDefault: Boolean = true) {
        if (folderName == "ทั่วไป" || folderName == "ทั้งหมด") return
        viewModelScope.launch {
            if (moveDocsToDefault) {
                repository.renameFolderInDocuments(folderName, "ทั่วไป")
            } else {
                val allDocs = repository.getAllDocumentsList()
                allDocs.filter { it.folder == folderName }.forEach { doc ->
                    deleteDocument(doc)
                }
            }
            val current = _customFolders.value.filter { it != folderName }
            saveFoldersToPrefs(current)
            if (_selectedFolder.value == folderName) {
                _selectedFolder.value = "ทั้งหมด"
            }
        }
    }

    fun moveDocumentToFolder(document: Document, targetFolder: String) {
        viewModelScope.launch {
            val updated = document.copy(folder = targetFolder)
            repository.updateDocument(updated)
            if (_currentDocument.value?.id == document.id) {
                _currentDocument.value = updated
            }
            if (syncManager.isAutoSyncEnabled()) {
                syncManager.backupSingleDocument(document.id)
            }
        }
    }

    fun moveBatchDocumentsToFolder(docIds: List<Long>, targetFolder: String) {
        viewModelScope.launch {
            repository.updateBatchDocumentFolders(docIds, targetFolder)
            if (syncManager.isAutoSyncEnabled()) {
                docIds.forEach { syncManager.backupSingleDocument(it) }
            }
        }
    }

    // Filtered list of documents based on query and selected folder
    val filteredDocuments: StateFlow<List<Pair<Document, List<ScannedPage>>>> = combine(
        allDocuments, _searchQuery, _selectedFolder
    ) { docs, query, folderFilter ->
        val results = mutableListOf<Pair<Document, List<ScannedPage>>>()
        for (doc in docs) {
            val matchesFolder = (folderFilter == "ทั้งหมด") || (doc.folder == folderFilter)
            if (!matchesFolder) continue

            val pages = repository.getPagesListForDocument(doc.id)
            if (query.isEmpty() || 
                doc.title.contains(query, ignoreCase = true) || 
                doc.folder.contains(query, ignoreCase = true) ||
                pages.any { it.ocrText.contains(query, ignoreCase = true) }
            ) {
                results.add(Pair(doc, pages))
            }
        }
        results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun selectDocument(document: Document?) {
        _currentDocument.value = document
        if (document != null) {
            viewModelScope.launch {
                repository.getPagesForDocument(document.id)
                    .collect { pages ->
                        _currentPages.value = pages
                        isRtlFlow.value = document.isRtl
                    }
            }
        } else {
            _currentPages.value = emptyList()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Set up a new document.
     */
    fun createNewDocument(title: String = "", folder: String = "ทั่วไป", onCreated: (Long) -> Unit = {}) {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val name = if (title.isNotEmpty()) title else "Scan_${dateFormat.format(Date())}"
        val targetFolder = folder.ifBlank { if (_selectedFolder.value != "ทั้งหมด") _selectedFolder.value else "ทั่วไป" }
        
        viewModelScope.launch {
            val newId = repository.insertDocument(Document(title = name, folder = targetFolder, isRtl = isRtlFlow.value))
            val newDoc = repository.getDocumentById(newId)
            selectDocument(newDoc)
            onCreated(newId)
        }
    }

    fun processBatchScannedPages(
        capturedPages: List<Pair<android.net.Uri, List<androidx.compose.ui.geometry.Offset>?>>,
        config: BatchScanConfig = BatchScanConfig(),
        onComplete: ((Document, File?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            
            isProcessingBatch.value = true
            batchTotal.value = capturedPages.size
            batchProgress.value = 0

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val docTitle = config.title.trim().ifEmpty { "Scan_${dateFormat.format(Date())}" }
            val docFolder = config.folder.trim().ifEmpty { if (_selectedFolder.value != "ทั้งหมด") _selectedFolder.value else "ทั่วไป" }

            val docId = repository.insertDocument(Document(
                title = docTitle, 
                folder = docFolder,
                isRtl = isRtlFlow.value
            ))
            val createdDoc = repository.getDocumentById(docId)
            selectDocument(createdDoc)

            val filterModeToApply = config.filterMode.ifBlank { activeFilterMode.value }

            for ((index, pageInfo) in capturedPages.withIndex()) {
                val uri = pageInfo.first
                val corners = pageInfo.second
                batchStatusText.value = "กำลังประมวลผลหน้าที่ ${index + 1} จาก ${capturedPages.size} (ปรับมุมมอง & ฟิลเตอร์)..."
                batchProgress.value = index

                // Read from URI
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } ?: continue

                val rawPath = saveBitmapToFile(context, bitmap, "raw_${System.currentTimeMillis()}_${index}.jpg")

                // Process image with auto document boundary quad detection (perspective crop)
                val cropPoints = if (corners != null && corners.size == 4) {
                    corners.map { android.graphics.PointF(it.x, it.y) }
                } else {
                    ImageProcessor.detectAutoDocumentQuad(bitmap)
                }

                val processedBitmap = ImageProcessor.processPage(
                    source = bitmap,
                    filterMode = filterModeToApply,
                    applyDeWarp = autoDewarpEnabled.value,
                    applyFingerRemoval = autoFingerRemovalEnabled.value,
                    cropPoints = cropPoints
                )
                val processedPath = saveBitmapToFile(context, processedBitmap, "proc_${System.currentTimeMillis()}_${index}.jpg")

                // Run optional OCR on page
                var recognizedOcr = ""
                if (config.runOcr) {
                    batchStatusText.value = "กำลังถอดข้อความ OCR หน้าที่ ${index + 1}/${capturedPages.size}..."
                    recognizedOcr = com.example.api.MlKitOcrService.performOcr(processedBitmap)
                }

                val newPage = ScannedPage(
                    documentId = docId,
                    pageNumber = index + 1,
                    originalImagePath = rawPath,
                    processedImagePath = processedPath,
                    ocrText = recognizedOcr,
                    hasDeWarp = autoDewarpEnabled.value,
                    hasFingerRemoved = autoFingerRemovalEnabled.value,
                    filterMode = filterModeToApply
                )
                repository.insertPage(newPage)
            }

            // Retrieve updated document and its pages
            val updatedDoc = repository.getDocumentById(docId) ?: Document(id = docId, title = docTitle)
            val allPages = repository.getPagesListForDocument(docId)
            selectDocument(updatedDoc)

            // Auto-Generate Multi-Page PDF file if requested
            var generatedPdfFile: File? = null
            if (config.autoGeneratePdf && allPages.isNotEmpty()) {
                batchStatusText.value = "กำลังรวมหน้าเอกสารและสร้างไฟล์ PDF หลายหน้า..."
                generatedPdfFile = com.example.util.ExportUtility.exportToPdf(
                    context = context,
                    document = updatedDoc,
                    pages = allPages,
                    customFileName = updatedDoc.title
                )
            }

            // Cloud sync if enabled
            if (syncManager.isAutoSyncEnabled()) {
                syncManager.backupSingleDocument(docId)
            }

            val displayTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            _batchResultState.value = BatchResult(
                document = updatedDoc,
                pdfFile = generatedPdfFile,
                pageCount = allPages.size,
                generatedTime = displayTime
            )

            batchStatusText.value = ""
            isProcessingBatch.value = false

            onComplete?.invoke(updatedDoc, generatedPdfFile)
        }
    }

    fun addCapturedPages(
        capturedPages: List<Pair<android.net.Uri, List<androidx.compose.ui.geometry.Offset>?>>,
        onComplete: ((Document) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            
            isProcessingBatch.value = true
            batchTotal.value = capturedPages.size
            batchProgress.value = 0
            
            var docId = _currentDocument.value?.id ?: 0
            if (docId == 0L) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val targetFolder = if (_selectedFolder.value != "ทั้งหมด") _selectedFolder.value else "ทั่วไป"
                docId = repository.insertDocument(Document(
                    title = "Scan_${dateFormat.format(Date())}", 
                    folder = targetFolder,
                    isRtl = isRtlFlow.value
                ))
                val newDoc = repository.getDocumentById(docId)
                selectDocument(newDoc)
            }

            var nextPageNum = (_currentPages.value.maxByOrNull { it.pageNumber }?.pageNumber ?: 0) + 1
            
            for ((index, pageInfo) in capturedPages.withIndex()) {
                val uri = pageInfo.first
                val corners = pageInfo.second
                batchStatusText.value = "กำลังประมวลผลหน้าที่ ${index + 1} จาก ${capturedPages.size}..."
                batchProgress.value = index
                
                // Read from URI
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } ?: continue

                val rawPath = saveBitmapToFile(context, bitmap, "raw_${System.currentTimeMillis()}.jpg")
                
                // Process image with OpenCV & ML Kit auto document boundary detection (perspective crop)
                val cropPoints = if (corners != null && corners.size == 4) {
                    corners.map { android.graphics.PointF(it.x, it.y) }
                } else {
                    ImageProcessor.detectAutoDocumentQuad(bitmap)
                }

                val processedBitmap = ImageProcessor.processPage(
                    source = bitmap,
                    filterMode = activeFilterMode.value,
                    applyDeWarp = autoDewarpEnabled.value,
                    applyFingerRemoval = autoFingerRemovalEnabled.value,
                    cropPoints = cropPoints
                )
                val processedPath = saveBitmapToFile(context, processedBitmap, "proc_${System.currentTimeMillis()}.jpg")

                val newPage = ScannedPage(
                    documentId = docId,
                    pageNumber = nextPageNum,
                    originalImagePath = rawPath,
                    processedImagePath = processedPath,
                    hasDeWarp = autoDewarpEnabled.value,
                    hasFingerRemoved = autoFingerRemovalEnabled.value,
                    filterMode = activeFilterMode.value
                )
                repository.insertPage(newPage)
                nextPageNum++
            }
            
            // Refresh
            val updatedDoc = repository.getDocumentById(docId)
            selectDocument(updatedDoc)
            
            batchStatusText.value = ""
            isProcessingBatch.value = false
            
            updatedDoc?.let { onComplete?.invoke(it) }
        }
    }

    fun addExternalPages(
        imageUris: List<android.net.Uri>, 
        corners: List<androidx.compose.ui.geometry.Offset>? = null,
        customTitle: String? = null,
        customFolder: String? = null,
        isBookMode: Boolean = false,
        onComplete: ((Document) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            
            isProcessingBatch.value = true
            batchTotal.value = imageUris.size
            batchProgress.value = 0
            
            var docId = _currentDocument.value?.id ?: 0
            if (docId == 0L || customTitle != null) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val targetFolder = customFolder ?: if (_selectedFolder.value != "ทั้งหมด") _selectedFolder.value else "ทั่วไป"
                val finalTitle = customTitle?.trim()?.ifEmpty { null } ?: "นำเข้า_${dateFormat.format(Date())}"
                docId = repository.insertDocument(Document(
                    title = finalTitle, 
                    folder = targetFolder,
                    isRtl = isRtlFlow.value
                ))
                val newDoc = repository.getDocumentById(docId)
                selectDocument(newDoc)
            }

            var nextPageNum = (_currentPages.value.maxByOrNull { it.pageNumber }?.pageNumber ?: 0) + 1
            
            for ((index, uri) in imageUris.withIndex()) {
                batchStatusText.value = "กำลังนำเข้าและประมวลผลรูปที่ ${index + 1} จาก ${imageUris.size}..."
                batchProgress.value = index
                
                // Read from URI with EXIF orientation correction
                val decodedBitmap = withContext(Dispatchers.IO) {
                    ImageProcessor.decodeBitmapWithExif(context, uri)
                } ?: continue

                // Check if book mode is active (split into left & right pages)
                val bitmapsToProcess = if (isBookMode) {
                    val (left, right) = ImageProcessor.splitBookBitmap(decodedBitmap)
                    listOf(left, right)
                } else {
                    listOf(decodedBitmap)
                }

                for (bitmap in bitmapsToProcess) {
                    val rawPath = saveBitmapToFile(context, bitmap, "raw_${System.currentTimeMillis()}.jpg")
                    
                    // Process external image with OpenCV & ML Kit auto boundary detection
                    val cropPoints = if (corners != null && corners.size == 4) {
                        corners.map { android.graphics.PointF(it.x, it.y) }
                    } else {
                        ImageProcessor.detectAutoDocumentQuad(bitmap)
                    }

                    val processedBitmap = ImageProcessor.processPage(
                        source = bitmap,
                        filterMode = activeFilterMode.value,
                        applyDeWarp = autoDewarpEnabled.value,
                        applyFingerRemoval = autoFingerRemovalEnabled.value,
                        cropPoints = cropPoints
                    )
                    val processedPath = saveBitmapToFile(context, processedBitmap, "proc_${System.currentTimeMillis()}.jpg")

                    val newPage = ScannedPage(
                        documentId = docId,
                        pageNumber = nextPageNum,
                        originalImagePath = rawPath,
                        processedImagePath = processedPath,
                        ocrText = "Pending OCR Process...",
                        hasDeWarp = true,
                        hasFingerRemoved = autoFingerRemovalEnabled.value,
                        filterMode = activeFilterMode.value
                    )

                    repository.insertPage(newPage)
                    nextPageNum++
                }
                
                // Let the UI render the progress animation beautifully
                delay(120)
            }
            batchProgress.value = imageUris.size
            isProcessingBatch.value = false
            
            val finalDoc = repository.getDocumentById(docId)
            if (finalDoc != null) {
                selectDocument(finalDoc)
                onComplete?.invoke(finalDoc)
            }
        }
    }

    /**
     * Simulates scanning a book page. Generates mock scanned input, saves it to storage,
     * processes dewarping/enhancement and inserts it as a new ScannedPage.
     */
    fun performScan(mockType: MockDocumentGenerator.MockDocType, isLeftPage: Boolean = true, customs: Bitmap? = null) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            
            // Generate source bitmap
            val rawBitmap = customs ?: MockDocumentGenerator.generateMockScan(mockType, isLeftPage)
            
            // Save raw image file
            val rawPath = saveBitmapToFile(context, rawBitmap, "raw_${System.currentTimeMillis()}.jpg")
            
            // Process image based on presets
            val processedBitmap = ImageProcessor.processPage(
                source = rawBitmap,
                filterMode = activeFilterMode.value,
                applyDeWarp = autoDewarpEnabled.value,
                applyFingerRemoval = autoFingerRemovalEnabled.value,
                cropPoints = MockDocumentGenerator.getMockCropPoints(mockType)
            )
            val processedPath = saveBitmapToFile(context, processedBitmap, "proc_${System.currentTimeMillis()}.jpg")

            // Determine active doc ID. If none, create one
            var docId = _currentDocument.value?.id ?: 0
            if (docId == 0L) {
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val targetFolder = if (_selectedFolder.value != "ทั้งหมด") _selectedFolder.value else "ทั่วไป"
                docId = repository.insertDocument(Document(
                    title = "Scan_${dateFormat.format(Date())}", 
                    folder = targetFolder,
                    isRtl = isRtlFlow.value
                ))
                val newDoc = repository.getDocumentById(docId)
                selectDocument(newDoc)
            }

            // Create page entity
            val nextPageNum = (_currentPages.value.maxByOrNull { it.pageNumber }?.pageNumber ?: 0) + 1
            val newPage = ScannedPage(
                documentId = docId,
                pageNumber = nextPageNum,
                originalImagePath = rawPath,
                processedImagePath = processedPath,
                ocrText = "Pending OCR Process...",
                hasDeWarp = autoDewarpEnabled.value,
                hasFingerRemoved = autoFingerRemovalEnabled.value,
                filterMode = activeFilterMode.value
            )

            repository.insertPage(newPage)
        }
    }

    /**
     * Batch scanned 2 pages support (open book captures)
     */
    fun performDoublePageScan(mockType: MockDocumentGenerator.MockDocType) {
        viewModelScope.launch {
            // Under Double Page mode, we capture LEFT and RIGHT simultaneously
            // and save them split sequentially.
            // If Right to Left (RTL) mode is enabled, we record the RIGHT page first, then the LEFT page.
            if (isRtlFlow.value) {
                // RTL: Right Page first (page 1), then Left Page (page 2)
                performScan(mockType, isLeftPage = false) // Right
                performScan(mockType, isLeftPage = true)  // Left
            } else {
                // LTR: Left Page first (page 1), then Right Page (page 2)
                performScan(mockType, isLeftPage = true)  // Left
                performScan(mockType, isLeftPage = false) // Right
            }
        }
    }

    /**
     * Updates an existing page with new dewarp, finger removal, color filter configs or crop bounds.
     */
    fun reprocessPage(
        page: ScannedPage,
        filter: String,
        dewarp: Boolean,
        finger: Boolean,
        cropPoints: List<PointF>? = null,
        brightness: Float = 0f
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val originalBitmap = withContext(Dispatchers.IO) {
                val file = File(page.originalImagePath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } ?: return@launch

            val processed = ImageProcessor.processPage(
                source = originalBitmap,
                filterMode = filter,
                applyDeWarp = dewarp,
                applyFingerRemoval = finger,
                cropPoints = cropPoints,
                brightness = brightness
            )
            val newProcessedPath = saveBitmapToFile(context, processed, "proc_${System.currentTimeMillis()}.jpg")

            // Delete old processed file
            try {
                File(page.processedImagePath).delete()
            } catch (e: Exception) {}

            val updatedPage = page.copy(
                processedImagePath = newProcessedPath,
                filterMode = filter,
                hasDeWarp = dewarp,
                hasFingerRemoved = finger,
                brightness = brightness,
                cropLeft = cropPoints?.getOrNull(0)?.x ?: page.cropLeft,
                cropTop = cropPoints?.getOrNull(0)?.y ?: page.cropTop,
                cropRight = cropPoints?.getOrNull(2)?.x ?: page.cropRight,
                cropBottom = cropPoints?.getOrNull(2)?.y ?: page.cropBottom
            )
            repository.updatePage(updatedPage)
        }
    }

    fun deletePage(page: ScannedPage) {
        viewModelScope.launch {
            // Delete files
            try { File(page.originalImagePath).delete() } catch (e: Exception) {}
            try { File(page.processedImagePath).delete() } catch (e: Exception) {}
            repository.deletePage(page)
        }
    }

    fun reorderPages(reorderedList: List<ScannedPage>) {
        viewModelScope.launch {
            reorderedList.forEachIndexed { index, page ->
                val updatedPage = page.copy(pageNumber = index + 1)
                repository.updatePage(updatedPage)
            }
        }
    }

    fun renameDocument(document: Document, newTitle: String) {
        viewModelScope.launch {
            val updated = document.copy(title = newTitle)
            repository.updateDocument(updated)
            if (_currentDocument.value?.id == document.id) {
                _currentDocument.value = updated
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            // Delete all associated files
            val pages = repository.getPagesListForDocument(document.id)
            for (p in pages) {
                try { File(p.originalImagePath).delete() } catch (e: Exception) {}
                try { File(p.processedImagePath).delete() } catch (e: Exception) {}
            }
            repository.deleteDocument(document)
            if (syncManager.isAutoSyncEnabled()) {
                syncManager.deleteDocumentFromCloud(document.id)
            }
            if (_currentDocument.value?.id == document.id) {
                selectDocument(null)
            }
        }
    }

    fun setRtlOrder(enabled: Boolean) {
        isRtlFlow.value = enabled
        val doc = _currentDocument.value
        if (doc != null) {
            viewModelScope.launch {
                repository.updateDocument(doc.copy(isRtl = enabled))
            }
        }
    }


    // --- OCR OPERATION ---
    fun runOcrOnPage(page: ScannedPage) {
        viewModelScope.launch {
            _ocrStatus.value = OcrStatus.Running(page.id)
            val bitmap = withContext(Dispatchers.IO) {
                val file = File(page.processedImagePath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }

            if (bitmap == null) {
                _ocrStatus.value = OcrStatus.Error("Failed to load processed image of page ${page.pageNumber}")
                return@launch
            }

            val result = com.example.api.MlKitOcrService.performOcr(bitmap)
            
            if (result.startsWith("OCR Error")) {
                _ocrStatus.value = OcrStatus.Error(result)
            } else {
                // Successfully parsed text
                val updatedPage = page.copy(ocrText = result)
                repository.updatePage(updatedPage)
                _ocrStatus.value = OcrStatus.Success(page.id, result)
            }
        }
    }


    // --- HELPERS ---
    private suspend fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): String = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        
        // Scale bitmap based on resolution selection
        val finalBitmap = if (!scanResolutionHigh.value) {
            // Downscale to 300 DPI (simulated: maximum dimension of 2048px)
            val maxDim = 2048
            if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
        } else {
            // Keep full high-resolution (600 DPI)
            bitmap
        }

        FileOutputStream(file).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        // Recycle the scaled copy to free memory immediately
        if (finalBitmap != bitmap) {
            finalBitmap.recycle()
        }
        
        file.absolutePath
    }
}

sealed class OcrStatus {
    object Idle : OcrStatus()
    data class Running(val pageId: Long) : OcrStatus()
    data class Success(val pageId: Long, val text: String) : OcrStatus()
    data class Error(val error: String) : OcrStatus()
}
