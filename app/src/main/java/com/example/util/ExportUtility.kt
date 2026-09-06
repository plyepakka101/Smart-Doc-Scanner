package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.Document
import com.example.data.ScannedPage
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.UUID

object ExportUtility {

    fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "แชร์ไปยัง (Share via)...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "ไม่สามารถแชร์ได้: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdfFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "ไม่พบแอปพลิเคชันสำหรับเปิดอ่าน PDF (กรุณาแชร์ไฟล์แทน): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareMultipleFiles(context: Context, files: List<File>, mimeType: String) {
        try {
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "แชร์ไฟล์ทั้งหมดไปยัง (Share all via)...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "ไม่สามารถแชร์ได้: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Combines multiple document collections into a single merged PDF file.
     */
    suspend fun exportMultipleDocumentsToPdf(
        context: Context,
        docsWithPages: List<Pair<Document, List<ScannedPage>>>,
        mergedFileName: String = "Merged_Document_Collection"
    ): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val allPages = docsWithPages.flatMap { it.second }
        if (allPages.isEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "ไม่มีหน้าที่ต้องการส่งออก!", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        try {
            val pdfDocument = PdfDocument()
            var pageIndex = 1

            for ((_, pages) in docsWithPages) {
                for (page in pages) {
                    val file = File(page.processedImagePath)
                    if (!file.exists()) continue

                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)

                    val canvas: Canvas = pdfPage.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(pdfPage)
                    bitmap.recycle()
                    pageIndex++
                }
            }

            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeTitle = mergedFileName.replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            val pdfFile = File(targetDir, "${safeTitle}.pdf")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            return@withContext pdfFile
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Export PDF Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }

    /**
     * Builds and exports the scanned document as a multi-page PDF document.
     * Uses built-in Android PdfDocument.
     */
    suspend fun exportToPdf(context: Context, document: Document, pages: List<ScannedPage>, customFileName: String? = null): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (pages.isEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "No pages to export!", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        try {
            val pdfDocument = PdfDocument()
            
            for ((index, page) in pages.withIndex()) {
                val file = File(page.processedImagePath)
                if (!file.exists()) continue

                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                
                // Create a page with the same dimensions as the processed image
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                
                // Draw image to PDF page
                val canvas: Canvas = pdfPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)
                bitmap.recycle() // HIGHLY IMPORTANT TO AVOID OOM
            }

            // Save file in public document directory or app external files to be easily shared
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeTitle = customFileName?.replace("\\s+".toRegex(), "_")?.replace("[^a-zA-Z0-9_\\-]".toRegex(), "") 
                ?: document.title.replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            val pdfFile = File(targetDir, "${safeTitle}.pdf")
            
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // Toast removed here to prevent spamming, or we can leave it
            }
            return@withContext pdfFile
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "PDF Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }

    /**
     * Exports all parsed text from a document's OCR into a single plain text file.
     */
    suspend fun exportToTxt(context: Context, document: Document, pages: List<ScannedPage>, customFileName: String? = null): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeTitle = customFileName?.replace("\\s+".toRegex(), "_")?.replace("[^a-zA-Z0-9_\\-]".toRegex(), "") 
                ?: document.title.replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            val txtFile = File(targetDir, "${safeTitle}.txt")

            FileWriter(txtFile).use { writer ->
                writer.write("====================================================\n")
                writer.write("DOCUMENT OCR REPORT: ${document.title.uppercase()}\n")
                writer.write("Created on: ${java.util.Date(document.createdTime)}\n")
                writer.write("====================================================\n\n")

                for (page in pages) {
                    writer.write("--- PAGE ${page.pageNumber} ---\n")
                    if (page.ocrText.isNotEmpty() && page.ocrText != "Pending OCR Process...") {
                        writer.write(page.ocrText)
                    } else {
                        writer.write("[No OCR text available for this page]")
                    }
                    writer.write("\n\n")
                }
            }

            return@withContext txtFile
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Text Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }

    /**
     * Exports as rich doc-equivalent containing structured HTML tags.
     */
    suspend fun exportToWord(context: Context, document: Document, pages: List<ScannedPage>, customFileName: String? = null): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null
        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeTitle = customFileName?.replace("\\s+".toRegex(), "_")?.replace("[^a-zA-Z0-9_\\-]".toRegex(), "") 
                ?: document.title.replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            val docFile = File(targetDir, "${safeTitle}.doc")

            FileWriter(docFile).use { writer ->
                writer.write("<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>\n")
                writer.write("<head><title>${document.title}</title><style>body { font-family: 'Arial', sans-serif; }</style></head>\n")
                writer.write("<body>\n")
                writer.write("<h1 style='color:#1a73e8; text-align:center;'>${document.title}</h1>\n")
                writer.write("<p style='text-align:center; color:gray;'>Saved using Smart Doc Scanner AI</p>\n")
                writer.write("<hr/>\n")

                for (page in pages) {
                    writer.write("<div style='margin-bottom: 40px;'>\n")
                    writer.write("<h3 style='color:#3c4043;'>Page ${page.pageNumber}</h3>\n")
                    
                    // Format lines with paragraphs
                    val formattedText = (page.ocrText.ifEmpty { "[No OCR Text Available]" })
                        .replace("\n", "<br/>")
                    
                    writer.write("<p style='font-size: 14px; line-height: 1.6; color:#202124;'>$formattedText</p>\n")
                    writer.write("</div>\n")
                }
                writer.write("</body></html>")
            }
            return@withContext docFile
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Word Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }

    /**
     * Generates a link to share the document via web hosting mock view.
     * Simply copies the link and displays a Toast!
     */
    fun shareWebLink(context: Context, document: Document) {
        val hash = UUID.nameUUIDFromBytes(document.id.toString().toByteArray()).toString().take(8)
        val shareLink = "https://ais-share-view-scanner.web.app/viewer?doc=$hash&title=${document.title.replace(" ", "%20")}"
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Document Web Share Link", shareLink)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(context, "แชร์ลิงก์คัดลอกลงคลิปบอร์ดแล้ว: Web link copied to clipboard!", Toast.LENGTH_LONG).show()
    }

    /**
     * Converts a single processed bitmap into a downloadable PDF document.
     */
    suspend fun exportBitmapToPdf(context: Context, bitmap: android.graphics.Bitmap, customFileName: String = "ExportedImage"): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            
            val canvas: Canvas = pdfPage.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(pdfPage)
            
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val safeTitle = customFileName.replace("\\s+".toRegex(), "_").replace("[^a-zA-Z0-9_\\-]".toRegex(), "")
            val pdfFile = File(targetDir, "${safeTitle}.pdf")
            
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            
            return@withContext pdfFile
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "PDF Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }
    }
}
