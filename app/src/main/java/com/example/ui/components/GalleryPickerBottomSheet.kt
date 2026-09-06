package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

enum class GalleryAppChoice {
    SYSTEM_CHOOSER,
    DEVICE_GALLERY,
    GOOGLE_PHOTOS,
    ANDROID_PHOTO_PICKER
}

class GalleryPickerLauncher(
    private val onOpenSheet: () -> Unit
) {
    fun launch() {
        onOpenSheet()
    }
}

/**
 * Extracts all selected image Uris from an Intent data object.
 * Handles both single selection (`data.data`) and multiple selection (`data.clipData`).
 */
fun extractUrisFromIntent(data: Intent?): List<Uri> {
    if (data == null) return emptyList()
    val list = mutableListOf<Uri>()
    data.data?.let { list.add(it) }
    data.clipData?.let { clipData ->
        for (i in 0 until clipData.itemCount) {
            val itemUri = clipData.getItemAt(i).uri
            if (!list.contains(itemUri)) {
                list.add(itemUri)
            }
        }
    }
    return list
}

/**
 * Registers launchers and returns a controller to trigger the multi-source gallery picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGalleryPickerLauncher(
    onImagesSelected: (List<Uri>) -> Unit
): GalleryPickerLauncher {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    // Launcher for standard Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImagesSelected(uris)
        }
    }

    // Launcher for Intent-based image pickers (Google Photos, Device Gallery, System Chooser)
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = extractUrisFromIntent(result.data)
            if (uris.isNotEmpty()) {
                onImagesSelected(uris)
            }
        }
    }

    fun launchSource(choice: GalleryAppChoice) {
        when (choice) {
            GalleryAppChoice.SYSTEM_CHOOSER -> {
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    val chooser = Intent.createChooser(intent, "เลือกรูปภาพด้วย...")
                    activityLauncher.launch(chooser)
                } catch (e: Exception) {
                    Toast.makeText(context, "ไม่สามารถเปิดระบบเลือกไฟล์ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            GalleryAppChoice.DEVICE_GALLERY -> {
                try {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    activityLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    // Fallback to System Chooser if device doesn't have standard ACTION_PICK handler
                    Toast.makeText(context, "เปิดแกลเลอรีของเครื่องผ่านระบบเลือก...", Toast.LENGTH_SHORT).show()
                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    activityLauncher.launch(Intent.createChooser(fallbackIntent, "เลือกแกลเลอรี..."))
                } catch (e: Exception) {
                    Toast.makeText(context, "ข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            GalleryAppChoice.GOOGLE_PHOTOS -> {
                try {
                    val intent = Intent(Intent.ACTION_PICK).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        setPackage("com.google.android.apps.photos")
                    }
                    activityLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "ไม่พบแอป Google Photos ในเครื่อง กำลังเปิดด้วยระบบเลือกแอป...", Toast.LENGTH_LONG).show()
                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    activityLauncher.launch(Intent.createChooser(fallbackIntent, "เลือกรูปภาพจาก Google Photos หรือแอปอื่น..."))
                } catch (e: Exception) {
                    Toast.makeText(context, "ไม่สามารถเปิด Google Photos: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            GalleryAppChoice.ANDROID_PHOTO_PICKER -> {
                try {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "ไม่สามารถเปิด Photo Picker ได้: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            GallerySourceSheetContent(
                onSelectSource = { choice ->
                    showSheet = false
                    launchSource(choice)
                },
                onDismiss = { showSheet = false }
            )
        }
    }

    return remember {
        GalleryPickerLauncher {
            showSheet = true
        }
    }
}

@Composable
fun GallerySourceSheetContent(
    onSelectSource: (GalleryAppChoice) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "เลือกแหล่งรูปภาพ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "เลือกแอปที่ต้องการเปิดเพื่อเลือกรูปภาพเข้าสู่เอกสาร",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "ปิด")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Option 1: System Chooser (Open with any app: Photos, Gallery, Files)
        GalleryOptionCard(
            title = "เปิดด้วยแอปในเครื่อง (System Chooser)",
            subtitle = "แสดงรายชื่อแอปทั้งหมดในเครื่อง (Google Photos, Gallery, Files)",
            badge = "แนะนำ",
            icon = Icons.Default.Apps,
            iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = { onSelectSource(GalleryAppChoice.SYSTEM_CHOOSER) }
        )

        // Option 2: Device Gallery
        GalleryOptionCard(
            title = "แกลเลอรีของเครื่อง (Device Gallery)",
            subtitle = "เปิดแอปแกลเลอรีประจำเครื่องโดยตรง",
            icon = Icons.Default.PhoneAndroid,
            iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = { onSelectSource(GalleryAppChoice.DEVICE_GALLERY) }
        )

        // Option 3: Google Photos
        GalleryOptionCard(
            title = "Google Photos",
            subtitle = "ค้นหารูปภาพและอัลบั้มใน Google Photos หรือบัญชีคลาวด์",
            icon = Icons.Default.Cloud,
            iconBackgroundColor = Color(0xFFE8F0FE),
            iconColor = Color(0xFF1A73E8),
            onClick = { onSelectSource(GalleryAppChoice.GOOGLE_PHOTOS) }
        )

        // Option 4: Android Photo Picker
        GalleryOptionCard(
            title = "ตัวเลือกรูปภาพด่วน (Photo Picker)",
            subtitle = "เลือกภาพทันทีผ่านอินเทอร์เฟซมาตรฐานของ Android",
            icon = Icons.Default.PhotoLibrary,
            iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = { onSelectSource(GalleryAppChoice.ANDROID_PHOTO_PICKER) }
        )
    }
}

@Composable
private fun GalleryOptionCard(
    title: String,
    subtitle: String,
    badge: String? = null,
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
