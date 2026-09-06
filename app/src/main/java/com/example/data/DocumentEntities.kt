package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdTime: Long = System.currentTimeMillis(),
    val isRtl: Boolean = false,
    val folder: String = "ทั่วไป"
)

@Entity(
    tableName = "scanned_pages",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class ScannedPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val ocrText: String = "",
    val hasDeWarp: Boolean = false,
    val hasFingerRemoved: Boolean = false,
    val filterMode: String = "ORIGINAL", // ORIGINAL, ENHANCED, BLACK_WHITE, GRAYSCALE
    val brightness: Float = 0.0f,
    // Normalized crop ratios (0.0 to 1.0)
    val cropLeft: Float = 0.0f,
    val cropTop: Float = 0.0f,
    val cropRight: Float = 1.0f,
    val cropBottom: Float = 1.0f
)
