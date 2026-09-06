package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SyncStatus {
    object Idle : SyncStatus()
    data class Syncing(val message: String = "กำลังเชื่อมต่อกับ Cloud Firestore...") : SyncStatus()
    data class Success(val message: String, val lastSyncTime: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

data class CloudSyncSummary(
    val cloudDocumentCount: Int = 0,
    val localDocumentCount: Int = 0,
    val lastSyncTime: String = "ยังไม่มีการซิงค์",
    val autoSyncEnabled: Boolean = true
)

class FirestoreSyncManager(
    private val context: Context,
    private val repository: DocumentRepository
) {

    private val prefs = context.getSharedPreferences("scanner_cloud_sync_prefs", Context.MODE_PRIVATE)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private val _syncSummary = MutableStateFlow(
        CloudSyncSummary(
            lastSyncTime = prefs.getString("last_sync_time", "ยังไม่มีการซิงค์") ?: "ยังไม่มีการซิงค์",
            autoSyncEnabled = prefs.getBoolean("auto_sync_enabled", true)
        )
    )
    val syncSummary: StateFlow<CloudSyncSummary> = _syncSummary

    private var db: FirebaseFirestore? = null

    init {
        try {
            db = FirebaseFirestore.getInstance()
            Log.d("FirestoreSyncManager", "Firebase Firestore instance initialized successfully")
        } catch (e: Throwable) {
            Log.w("FirestoreSyncManager", "Firebase not initialized: ${e.message}")
            _syncStatus.value = SyncStatus.Error("Firebase Firestore ต้องใช้การตั้งค่าโครงการ (google-services.json)")
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
        _syncSummary.value = _syncSummary.value.copy(autoSyncEnabled = enabled)
    }

    fun isAutoSyncEnabled(): Boolean {
        return _syncSummary.value.autoSyncEnabled
    }

    private fun getCurrentFormattedTime(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun encodeImageToBase64(imagePath: String): String? {
        if (imagePath.isBlank()) return null
        val file = File(imagePath)
        if (!file.exists()) return null

        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val maxDimension = 1200
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    Bitmap.createScaledBitmap(bitmap, maxDimension, (maxDimension / ratio).toInt(), true)
                } else {
                    Bitmap.createScaledBitmap(bitmap, (maxDimension * ratio).toInt(), maxDimension, true)
                }
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to encode image to base64: ${e.message}")
            null
        }
    }

    private fun decodeBase64ToImageFile(base64Str: String, docId: Long, pageNum: Int): String? {
        if (base64Str.isBlank()) return null
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.NO_WRAP)
            val fileName = "cloud_restored_${docId}_page_${pageNum}_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val fos = FileOutputStream(file)
            fos.write(decodedBytes)
            fos.flush()
            fos.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to decode base64 to image file: ${e.message}")
            null
        }
    }

    suspend fun backupSingleDocument(docId: Long) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        try {
            val doc = repository.documentDao.getDocumentById(docId) ?: return@withContext
            val pages = repository.getPagesListForDocument(docId)

            val docMap = hashMapOf(
                "id" to doc.id,
                "title" to doc.title,
                "folder" to doc.folder,
                "createdTime" to doc.createdTime,
                "isRtl" to doc.isRtl,
                "pageCount" to pages.size,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection("documents")
                .document("doc_${doc.id}")
                .set(docMap, SetOptions.merge())
                .await()

            for (page in pages) {
                val imagePath = page.processedImagePath.ifBlank { page.originalImagePath }
                val imageBase64 = encodeImageToBase64(imagePath) ?: ""

                val pageMap = hashMapOf(
                    "pageNumber" to page.pageNumber,
                    "ocrText" to page.ocrText,
                    "filterMode" to page.filterMode,
                    "hasDeWarp" to page.hasDeWarp,
                    "hasFingerRemoved" to page.hasFingerRemoved,
                    "brightness" to page.brightness,
                    "cropLeft" to page.cropLeft,
                    "cropTop" to page.cropTop,
                    "cropRight" to page.cropRight,
                    "cropBottom" to page.cropBottom,
                    "imageBase64" to imageBase64
                )

                firestore.collection("documents")
                    .document("doc_${doc.id}")
                    .collection("pages")
                    .document("page_${page.pageNumber}")
                    .set(pageMap, SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Auto backup single doc failed: ${e.message}")
        }
    }

    suspend fun deleteDocumentFromCloud(docId: Long) = withContext(Dispatchers.IO) {
        val firestore = db ?: return@withContext
        try {
            val docRef = firestore.collection("documents").document("doc_$docId")
            val pagesSnap = docRef.collection("pages").get().await()
            for (pageDoc in pagesSnap.documents) {
                pageDoc.reference.delete().await()
            }
            docRef.delete().await()
            Log.d("FirestoreSyncManager", "Document $docId deleted from Firestore")
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to delete doc $docId from Firestore: ${e.message}")
        }
    }

    suspend fun backupAllToCloud() = withContext(Dispatchers.IO) {
        val firestore = db
        if (firestore == null) {
            _syncStatus.value = SyncStatus.Error("Firebase Firestore ยังไม่ได้ถูกเริ่มต้น กรุณาตรวจสอบการตั้งค่าโครงการ")
            return@withContext
        }

        _syncStatus.value = SyncStatus.Syncing("กำลังอัปโหลดเอกสารทั้งหมดขึ้น Cloud Firestore...")
        try {
            val documents = repository.documentDao.getAllDocumentsList()
            var backedUpCount = 0
            var totalPagesUploaded = 0

            for ((index, doc) in documents.withIndex()) {
                _syncStatus.value = SyncStatus.Syncing("กำลังสำรองเอกสารที่ ${index + 1}/${documents.size}: ${doc.title}")
                val pages = repository.getPagesListForDocument(doc.id)

                val docMap = hashMapOf(
                    "id" to doc.id,
                    "title" to doc.title,
                    "folder" to doc.folder,
                    "createdTime" to doc.createdTime,
                    "isRtl" to doc.isRtl,
                    "pageCount" to pages.size,
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("documents")
                    .document("doc_${doc.id}")
                    .set(docMap, SetOptions.merge())
                    .await()

                for (page in pages) {
                    val imagePath = page.processedImagePath.ifBlank { page.originalImagePath }
                    val imageBase64 = encodeImageToBase64(imagePath) ?: ""

                    val pageMap = hashMapOf(
                        "pageNumber" to page.pageNumber,
                        "ocrText" to page.ocrText,
                        "filterMode" to page.filterMode,
                        "hasDeWarp" to page.hasDeWarp,
                        "hasFingerRemoved" to page.hasFingerRemoved,
                        "brightness" to page.brightness,
                        "cropLeft" to page.cropLeft,
                        "cropTop" to page.cropTop,
                        "cropRight" to page.cropRight,
                        "cropBottom" to page.cropBottom,
                        "imageBase64" to imageBase64
                    )

                    firestore.collection("documents")
                        .document("doc_${doc.id}")
                        .collection("pages")
                        .document("page_${page.pageNumber}")
                        .set(pageMap, SetOptions.merge())
                        .await()
                    
                    totalPagesUploaded++
                }

                backedUpCount++
            }

            val syncTime = getCurrentFormattedTime()
            prefs.edit().putString("last_sync_time", syncTime).apply()
            _syncSummary.value = _syncSummary.value.copy(
                cloudDocumentCount = backedUpCount,
                localDocumentCount = documents.size,
                lastSyncTime = syncTime
            )

            _syncStatus.value = SyncStatus.Success(
                message = "สำรองข้อมูล $backedUpCount เอกสาร ($totalPagesUploaded หน้า) ไปยัง Cloud Firestore สำเร็จ!",
                lastSyncTime = syncTime
            )
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Cloud sync failed: ", e)
            _syncStatus.value = SyncStatus.Error("การซิงค์ข้อมูลล้มเหลว: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun restoreFromCloud() = withContext(Dispatchers.IO) {
        val firestore = db
        if (firestore == null) {
            _syncStatus.value = SyncStatus.Error("Firebase Firestore ยังไม่ได้ถูกเริ่มต้น กรุณาตรวจสอบการตั้งค่าโครงการ")
            return@withContext
        }

        _syncStatus.value = SyncStatus.Syncing("กำลังดึงข้อมูลเอกสารจาก Cloud Firestore...")
        try {
            val snapshot = firestore.collection("documents").get().await()
            var restoredCount = 0
            var totalPagesRestored = 0

            val existingDocs = repository.documentDao.getAllDocumentsList()
            val existingTitles = existingDocs.map { it.title }.toSet()

            for ((index, docSnap) in snapshot.documents.withIndex()) {
                val title = docSnap.getString("title") ?: "Cloud Document"
                val folder = docSnap.getString("folder") ?: "ทั่วไป"
                val createdTime = docSnap.getLong("createdTime") ?: System.currentTimeMillis()
                val isRtl = docSnap.getBoolean("isRtl") ?: false

                _syncStatus.value = SyncStatus.Syncing("กำลังดาวน์โหลดเอกสารที่ ${index + 1}/${snapshot.documents.size}: $title")

                // Avoid exact duplicate naming confusion
                val finalTitle = if (existingTitles.contains(title)) {
                    "$title (Cloud Sync)"
                } else {
                    title
                }

                val newDocId = repository.insertDocument(
                    Document(title = finalTitle, createdTime = createdTime, isRtl = isRtl, folder = folder)
                )

                val pagesSnap = docSnap.reference.collection("pages").get().await()
                if (!pagesSnap.isEmpty) {
                    for (pageDoc in pagesSnap.documents) {
                        val pageNum = pageDoc.getLong("pageNumber")?.toInt() ?: 1
                        val ocrText = pageDoc.getString("ocrText") ?: ""
                        val filterMode = pageDoc.getString("filterMode") ?: "ORIGINAL"
                        val hasDeWarp = pageDoc.getBoolean("hasDeWarp") ?: false
                        val hasFingerRemoved = pageDoc.getBoolean("hasFingerRemoved") ?: false
                        val brightness = pageDoc.getDouble("brightness")?.toFloat() ?: 1.0f
                        val cropLeft = pageDoc.getDouble("cropLeft")?.toFloat() ?: 0f
                        val cropTop = pageDoc.getDouble("cropTop")?.toFloat() ?: 0f
                        val cropRight = pageDoc.getDouble("cropRight")?.toFloat() ?: 1f
                        val cropBottom = pageDoc.getDouble("cropBottom")?.toFloat() ?: 1f
                        val imageBase64 = pageDoc.getString("imageBase64") ?: ""

                        val localImagePath = decodeBase64ToImageFile(imageBase64, newDocId, pageNum) ?: ""

                        repository.insertPage(
                            ScannedPage(
                                documentId = newDocId,
                                pageNumber = pageNum,
                                originalImagePath = localImagePath,
                                processedImagePath = localImagePath,
                                ocrText = ocrText,
                                filterMode = filterMode,
                                hasDeWarp = hasDeWarp,
                                hasFingerRemoved = hasFingerRemoved,
                                brightness = brightness,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom
                            )
                        )
                        totalPagesRestored++
                    }
                }

                restoredCount++
            }

            val syncTime = getCurrentFormattedTime()
            prefs.edit().putString("last_sync_time", syncTime).apply()
            _syncSummary.value = _syncSummary.value.copy(
                cloudDocumentCount = snapshot.documents.size,
                localDocumentCount = repository.documentDao.getAllDocumentsList().size,
                lastSyncTime = syncTime
            )

            _syncStatus.value = SyncStatus.Success(
                message = "กู้คืนและซิงค์คลังเอกสาร $restoredCount ชุด ($totalPagesRestored หน้า) จาก Cloud เรียบร้อย!",
                lastSyncTime = syncTime
            )
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Cloud restore failed: ", e)
            _syncStatus.value = SyncStatus.Error("การกู้คืนข้อมูลล้มเหลว: ${e.localizedMessage ?: e.message}")
        }
    }

    suspend fun fullSync() = withContext(Dispatchers.IO) {
        // Upload local changes then check cloud
        backupAllToCloud()
    }
}
