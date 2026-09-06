package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A zoomable image container supporting pinch-to-zoom, pan gestures, double-tap zoom,
 * and quick floating zoom level controls (+, -, reset, percentage badge).
 */
@Composable
fun ZoomableImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 5.0f,
    showControls: Boolean = true,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val resetZoom = {
        scale = 1.0f
        offset = Offset.Zero
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.0f) {
                            scale = 1.0f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            offset = Offset(
                                (centerX - tapOffset.x) * (2.5f - 1f),
                                (centerY - tapOffset.y) * (2.5f - 1f)
                            )
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)

                    val newOffset = if (newScale != oldScale) {
                        val scaleFactor = newScale / oldScale
                        val newOffsetX = (offset.x - (centroid.x - containerSize.width / 2f)) * scaleFactor + (centroid.x - containerSize.width / 2f)
                        val newOffsetY = (offset.y - (centroid.y - containerSize.height / 2f)) * scaleFactor + (centroid.y - containerSize.height / 2f)
                        Offset(newOffsetX, newOffsetY) + pan
                    } else {
                        offset + pan
                    }

                    scale = newScale

                    if (scale > 1.0f && containerSize.width > 0 && containerSize.height > 0) {
                        val maxOffsetX = (containerSize.width * (scale - 1f)) / 2f
                        val maxOffsetY = (containerSize.height * (scale - 1f)) / 2f
                        offset = Offset(
                            newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                            newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        // Custom overlay content (such as interactive quad handles or bounding boxes)
        if (overlayContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                overlayContent()
            }
        }

        // Floating Zoom Level Controls & Badge
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val newScale = (scale - 0.5f).coerceIn(minScale, maxScale)
                            if (newScale == 1.0f) {
                                resetZoom()
                            } else {
                                scale = newScale
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = scale > minScale
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = if (scale > minScale) Color.White else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = {
                            scale = (scale + 0.5f).coerceIn(minScale, maxScale)
                        },
                        modifier = Modifier.size(28.dp),
                        enabled = scale < maxScale
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = if (scale < maxScale) Color.White else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (scale > 1.0f) {
                        HorizontalDivider(
                            modifier = Modifier
                                .height(14.dp)
                                .width(1.dp)
                                .padding(horizontal = 2.dp),
                            color = Color.White.copy(alpha = 0.3f)
                        )
                        IconButton(
                            onClick = { resetZoom() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Zoom",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
