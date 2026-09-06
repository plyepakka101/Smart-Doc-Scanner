package com.example.api

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object MlKitOcrService {
    suspend fun performOcr(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            "OCR Error: ${e.localizedMessage ?: "Unknown Error"}"
        }
    }

    /**
     * Uses ML Kit Text Recognition bounding boxes to detect document content bounds quad
     * as normalized coordinates (0.0 .. 1.0) representing TL, TR, BR, BL.
     */
    suspend fun detectTextBoundsQuad(bitmap: Bitmap): List<PointF>? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            var hasBlocks = false
            for (block in result.textBlocks) {
                block.boundingBox?.let { box ->
                    hasBlocks = true
                    if (box.left < minX) minX = box.left.toFloat()
                    if (box.top < minY) minY = box.top.toFloat()
                    if (box.right > maxX) maxX = box.right.toFloat()
                    if (box.bottom > maxY) maxY = box.bottom.toFloat()
                }
            }

            if (!hasBlocks) return null

            val width = bitmap.width.toFloat()
            val height = bitmap.height.toFloat()

            // Add margin around detected text area (7% of width/height)
            val padX = width * 0.07f
            val padY = height * 0.07f

            val left = (minX - padX).coerceIn(0f, width) / width
            val top = (minY - padY).coerceIn(0f, height) / height
            val right = (maxX + padX).coerceIn(0f, width) / width
            val bottom = (maxY + padY).coerceIn(0f, height) / height

            listOf(
                PointF(left, top),      // Top-Left
                PointF(right, top),     // Top-Right
                PointF(right, bottom),  // Bottom-Right
                PointF(left, bottom)    // Bottom-Left
            )
        } catch (e: Exception) {
            null
        }
    }
}
