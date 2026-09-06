package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

object ImageProcessor {

    init {
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Throwable) {
            try {
                org.opencv.android.OpenCVLoader.initDebug()
            } catch (t: Throwable) {
                // Ignore fallback
            }
        }
    }

    /**
     * Decodes a bitmap from an external Uri, correcting for EXIF orientation so it is upright.
     */
    fun decodeBitmapWithExif(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val rawBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (rawBitmap == null) return null

            val exifStream = context.contentResolver.openInputStream(uri) ?: return rawBitmap
            val exif = ExifInterface(exifStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            exifStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> null
            }
            if (matrix.isIdentity.not()) {
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                bmp
            } catch (ex: Exception) {
                null
            }
        }
    }

    /**
     * Splits a double-page book bitmap into Left and Right pages.
     */
    fun splitBookBitmap(source: Bitmap): Pair<Bitmap, Bitmap> {
        val width = source.width
        val height = source.height
        val halfWidth = width / 2
        val left = Bitmap.createBitmap(source, 0, 0, halfWidth, height)
        val right = Bitmap.createBitmap(source, halfWidth, 0, width - halfWidth, height)
        return Pair(left, right)
    }

    /**
     * Splits a book double-page photo into two separate page image files (Left Page & Right Page).
     * Automatically crops to book proportions (standard A4/A5 1.41:1 or specified aspect ratio),
     * applies de-warping and finger removal if requested, and produces two correctly proportioned pages.
     */
    fun splitBookPage(
        context: Context,
        originalUri: Uri,
        cropPoints: List<PointF>? = null,
        applyDeWarp: Boolean = false,
        applyFingerRemoval: Boolean = false,
        bookAspectRatio: Float = 1.414f
    ): List<Uri> {
        return try {
            val path = originalUri.path ?: return listOf(originalUri)
            val file = File(path)
            if (!file.exists()) return listOf(originalUri)

            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return listOf(originalUri)
            
            // Adjust for EXIF orientation so width & height match physical viewing
            val bitmap = try {
                val exif = ExifInterface(file.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    else -> null
                }
                if (matrix.isIdentity.not()) {
                    val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    if (rotated != rawBitmap) {
                        rawBitmap.recycle()
                    }
                    rotated
                } else {
                    rawBitmap
                }
            } catch (e: Exception) {
                rawBitmap
            }

            val origWidth = bitmap.width
            val origHeight = bitmap.height

            if (origWidth < 100 || origHeight < 100) return listOf(originalUri)

            val targetRatio = if (bookAspectRatio > 0.5f) bookAspectRatio else 1.414f

            // Step 1: Crop the book spread image to proper book proportions
            val bookSpreadBitmap: Bitmap = if (cropPoints != null && cropPoints.size == 4) {
                cropBitmap(bitmap, cropPoints)
            } else {
                // If no specific quad was detected, crop to the central book area matching targetRatio
                if (origHeight > origWidth) {
                    // Portrait orientation: book spread is horizontal across screen width
                    val cropW = (origWidth * 0.94f).toInt().coerceAtLeast(100)
                    val cropH = (cropW / targetRatio).toInt().coerceAtMost(origHeight)
                    val startX = ((origWidth - cropW) / 2).coerceAtLeast(0)
                    val startY = ((origHeight - cropH) / 2).coerceAtLeast(0)
                    Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
                } else {
                    // Landscape orientation
                    val cropH = (origHeight * 0.88f).toInt().coerceAtLeast(100)
                    val cropW = (cropH * targetRatio).toInt().coerceAtMost(origWidth)
                    val startX = ((origWidth - cropW) / 2).coerceAtLeast(0)
                    val startY = ((origHeight - cropH) / 2).coerceAtLeast(0)
                    Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
                }
            }

            if (bookSpreadBitmap != bitmap) {
                bitmap.recycle()
            }

            // Step 2: Optional de-warping for spine curvature
            val flattenedSpread = if (applyDeWarp) {
                try {
                    correctPageCurvature(bookSpreadBitmap)
                } catch (e: Exception) {
                    bookSpreadBitmap
                }
            } else {
                bookSpreadBitmap
            }

            val spreadW = flattenedSpread.width
            val spreadH = flattenedSpread.height
            val halfWidth = spreadW / 2

            // Step 3: Split into Left Page and Right Page down the spine
            var leftBitmap = Bitmap.createBitmap(flattenedSpread, 0, 0, halfWidth, spreadH)
            var rightBitmap = Bitmap.createBitmap(flattenedSpread, halfWidth, 0, spreadW - halfWidth, spreadH)

            if (flattenedSpread != bookSpreadBitmap && flattenedSpread != bitmap) {
                flattenedSpread.recycle()
            }
            if (bookSpreadBitmap != bitmap) {
                bookSpreadBitmap.recycle()
            }

            // Step 4: Optional finger removal from page margins
            if (applyFingerRemoval) {
                try {
                    leftBitmap = eraseFingersFromEdges(leftBitmap)
                    rightBitmap = eraseFingersFromEdges(rightBitmap)
                } catch (e: Exception) {
                    // ignore
                }
            }

            // Save both pages as separate high-quality images
            val leftFile = File(context.cacheDir, "book_left_${System.currentTimeMillis()}.jpg")
            FileOutputStream(leftFile).use { out ->
                leftBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            leftBitmap.recycle()

            val rightFile = File(context.cacheDir, "book_right_${System.currentTimeMillis() + 1}.jpg")
            FileOutputStream(rightFile).use { out ->
                rightBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            rightBitmap.recycle()

            listOf(Uri.fromFile(leftFile), Uri.fromFile(rightFile))
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(originalUri)
        }
    }

    /**
     * Applies a standard list of filters, dewarping, finger erasure, and brightness adjustment to a document bitmap.
     */
    fun processPage(
        source: Bitmap,
        filterMode: String,
        applyDeWarp: Boolean,
        applyFingerRemoval: Boolean,
        cropPoints: List<PointF>? = null,
        brightness: Float = 0f
    ): Bitmap {
        var result = source

        // 1. First, apply Crop / Border correction if specified
        if (cropPoints != null && cropPoints.size == 4) {
            result = cropBitmap(result, cropPoints)
        }

        // 2. Apply Page De-warping (Auto-Flattening simulation using vertical sinus dewarp)
        if (applyDeWarp) {
            result = correctPageCurvature(result)
        }

        // 3. Remove fingers near edges
        if (applyFingerRemoval) {
            result = eraseFingersFromEdges(result)
        }

        // 4. Apply Dynamic OpenCV Color filters and Brightness adjustment
        result = applyColorFilter(result, filterMode, brightness)

        return result
    }

    /**
     * Reconstructs/crops a quad defined by 4 normalized points into a perfectly aligned rectangular bitmap.
     */
    private fun cropBitmap(source: Bitmap, points: List<PointF>): Bitmap {
        val width = source.width
        val height = source.height

        // Convert normalized points relative to bitmap dimensions
        val p0 = org.opencv.core.Point(points[0].x.toDouble() * width, points[0].y.toDouble() * height) // Top-Left
        val p1 = org.opencv.core.Point(points[1].x.toDouble() * width, points[1].y.toDouble() * height) // Top-Right
        val p2 = org.opencv.core.Point(points[2].x.toDouble() * width, points[2].y.toDouble() * height) // Bottom-Right
        val p3 = org.opencv.core.Point(points[3].x.toDouble() * width, points[3].y.toDouble() * height) // Bottom-Left

        // Calculate maximum width and height
        val widthA = kotlin.math.hypot(p2.x - p3.x, p2.y - p3.y)
        val widthB = kotlin.math.hypot(p1.x - p0.x, p1.y - p0.y)
        val targetWidth = kotlin.math.max(widthA, widthB).toInt().coerceAtLeast(100)

        val heightA = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)
        val heightB = kotlin.math.hypot(p0.x - p3.x, p0.y - p3.y)
        val targetHeight = kotlin.math.max(heightA, heightB).toInt().coerceAtLeast(100)

        try {
            val srcMat = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(source, srcMat)

            val srcPoints = org.opencv.core.MatOfPoint2f(p0, p1, p2, p3)
            val dstPoints = org.opencv.core.MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(targetWidth.toDouble() - 1, 0.0),
                org.opencv.core.Point(targetWidth.toDouble() - 1, targetHeight.toDouble() - 1),
                org.opencv.core.Point(0.0, targetHeight.toDouble() - 1)
            )

            val transformMatrix = org.opencv.imgproc.Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            val dstMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.warpPerspective(
                srcMat, 
                dstMat, 
                transformMatrix, 
                org.opencv.core.Size(targetWidth.toDouble(), targetHeight.toDouble())
            )

            val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(dstMat, resultBitmap)

            srcMat.release()
            dstMat.release()
            transformMatrix.release()

            return resultBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback if OpenCV fails
            return source
        }
    }

    /**
     * Corrects the curvature of book pages using piecewise perspective transformation.
     */
    private fun correctPageCurvature(source: Bitmap): Bitmap {
        try {
            val srcMat = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(source, srcMat)
            
            val width = srcMat.cols()
            val height = srcMat.rows()
            val dstMat = org.opencv.core.Mat.zeros(height, width, srcMat.type())
            
            // Divide the image into vertical strips to apply piecewise perspective transformation
            val numStrips = 20
            val stripWidth = width.toDouble() / numStrips
            
            for (i in 0 until numStrips) {
                val x1 = i * stripWidth
                val x2 = (i + 1) * stripWidth
                
                // Calculate curve offsets (simulating a book spine in the middle)
                val nx1 = x1 / width
                val nx2 = x2 / width
                val shift1 = kotlin.math.sin(nx1 * Math.PI) * 20.0
                val shift2 = kotlin.math.sin(nx2 * Math.PI) * 20.0
                
                val srcPoints = org.opencv.core.MatOfPoint2f(
                    org.opencv.core.Point(x1, shift1),
                    org.opencv.core.Point(x2, shift2),
                    org.opencv.core.Point(x2, height - shift2),
                    org.opencv.core.Point(x1, height - shift1)
                )
                
                val dstPoints = org.opencv.core.MatOfPoint2f(
                    org.opencv.core.Point(x1, 0.0),
                    org.opencv.core.Point(x2, 0.0),
                    org.opencv.core.Point(x2, height.toDouble()),
                    org.opencv.core.Point(x1, height.toDouble())
                )
                
                val transform = org.opencv.imgproc.Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
                
                // We need to warp the whole image and copy the strip
                val warpedMat = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.warpPerspective(srcMat, warpedMat, transform, org.opencv.core.Size(width.toDouble(), height.toDouble()))
                
                // Copy just the strip region to the destination
                val roi = org.opencv.core.Rect(Math.round(x1).toInt(), 0, Math.round(stripWidth).toInt(), height)
                
                // Ensure ROI is within bounds
                val safeRoi = org.opencv.core.Rect(
                    kotlin.math.max(0, roi.x),
                    kotlin.math.max(0, roi.y),
                    kotlin.math.min(warpedMat.cols() - roi.x, roi.width),
                    kotlin.math.min(warpedMat.rows() - roi.y, roi.height)
                )
                
                if (safeRoi.width > 0 && safeRoi.height > 0) {
                    warpedMat.submat(safeRoi).copyTo(dstMat.submat(safeRoi))
                }
                
                warpedMat.release()
                transform.release()
                srcPoints.release()
                dstPoints.release()
            }
            
            val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(dstMat, resultBitmap)
            
            srcMat.release()
            dstMat.release()
            
            return resultBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return source
        }
    }

    /**
     * Erases thumbs/fingers holding book pages by detecting flesh colored pixels
     * near the border zones, replacing them via OpenCV inpainting.
     */
    private fun eraseFingersFromEdges(source: Bitmap): Bitmap {
        try {
            // Check if OpenCV is loaded by trying to instantiate Mat
            val srcMatRGBA = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(source, srcMatRGBA)
            
            // Convert to RGB first
            val srcMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(srcMatRGBA, srcMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
            
            // Convert to HSV to better isolate skin tones
            val hsvMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(srcMat, hsvMat, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)
            
            // Define skin color range in HSV.
            // OpenCV HSV range is H: 0-179, S: 0-255, V: 0-255
            val lowerSkin = org.opencv.core.Scalar(0.0, 20.0, 60.0)
            val upperSkin = org.opencv.core.Scalar(25.0, 255.0, 255.0)
            
            val mask = org.opencv.core.Mat()
            org.opencv.core.Core.inRange(hsvMat, lowerSkin, upperSkin, mask)
            
            // Second range for red-ish skin (H > 165)
            val lowerSkin2 = org.opencv.core.Scalar(165.0, 20.0, 60.0)
            val upperSkin2 = org.opencv.core.Scalar(179.0, 255.0, 255.0)
            val mask2 = org.opencv.core.Mat()
            org.opencv.core.Core.inRange(hsvMat, lowerSkin2, upperSkin2, mask2)
            
            org.opencv.core.Core.bitwise_or(mask, mask2, mask)
            
            // We only want to remove fingers near the edges of the image.
            // So we can multiply the mask by an edge-only stencil.
            val marginX = (srcMat.cols() * 0.15).toInt()
            val marginY = (srcMat.rows() * 0.15).toInt()
            
            // Clear center of mask
            val rectX1 = marginX
            val rectY1 = marginY
            val rectX2 = srcMat.cols() - marginX
            val rectY2 = srcMat.rows() - marginY
            
            if (rectX2 > rectX1 && rectY2 > rectY1) {
                val centerRect = org.opencv.core.Rect(rectX1, rectY1, rectX2 - rectX1, rectY2 - rectY1)
                val centerRoi = mask.submat(centerRect)
                centerRoi.setTo(org.opencv.core.Scalar(0.0))
            }
            
            // Refine the mask using morphological operations
            val kernel = org.opencv.imgproc.Imgproc.getStructuringElement(org.opencv.imgproc.Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(15.0, 15.0))
            org.opencv.imgproc.Imgproc.morphologyEx(mask, mask, org.opencv.imgproc.Imgproc.MORPH_CLOSE, kernel)
            org.opencv.imgproc.Imgproc.morphologyEx(mask, mask, org.opencv.imgproc.Imgproc.MORPH_OPEN, kernel)
            
            // Dilate mask slightly to cover finger edges completely
            val dilateKernel = org.opencv.imgproc.Imgproc.getStructuringElement(org.opencv.imgproc.Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(25.0, 25.0))
            org.opencv.imgproc.Imgproc.dilate(mask, mask, dilateKernel)
            
            // If the mask has any non-zero pixels, apply inpainting
            if (org.opencv.core.Core.countNonZero(mask) > 0) {
                val inpaintedMat = org.opencv.core.Mat()
                // inpaint needs 8-bit 1-channel or 3-channel image
                org.opencv.photo.Photo.inpaint(srcMat, mask, inpaintedMat, 5.0, org.opencv.photo.Photo.INPAINT_TELEA)
                
                // Convert back to Bitmap
                val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                org.opencv.android.Utils.matToBitmap(inpaintedMat, output)
                
                // Release mats
                srcMatRGBA.release(); srcMat.release(); hsvMat.release(); mask.release(); mask2.release(); inpaintedMat.release(); kernel.release(); dilateKernel.release()
                
                return output
            }
            
            // Release mats
            srcMatRGBA.release(); srcMat.release(); hsvMat.release(); mask.release(); mask2.release(); kernel.release(); dilateKernel.release()
            
            return source // Nothing to inpaint
            
        } catch (e: Exception) {
            e.printStackTrace()
            // If OpenCV fails, fallback to original dummy logic or just return source
            return source
        }
    }

    /**
     * Applies OpenCV-powered filters (Grayscale, Adaptive Binary B&W, Magic Color Enhancement) and brightness adjustment.
     */
    private fun applyColorFilter(source: Bitmap, mode: String, brightness: Float = 0f): Bitmap {
        try {
            val srcMat = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(source, srcMat)
            val dstMat = org.opencv.core.Mat()

            // 1. Apply global brightness adjustment using OpenCV convertScaleAbs if specified
            if (brightness != 0f) {
                org.opencv.core.Core.convertScaleAbs(srcMat, srcMat, 1.0, brightness.toDouble())
            }

            when (mode.uppercase()) {
                "GRAYSCALE" -> {
                    val grayMat = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.cvtColor(srcMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                    org.opencv.imgproc.Imgproc.cvtColor(grayMat, dstMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
                    grayMat.release()
                }
                "BLACK_WHITE", "BINARY" -> {
                    val grayMat = org.opencv.core.Mat()
                    val binMat = org.opencv.core.Mat()
                    org.opencv.imgproc.Imgproc.cvtColor(srcMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
                    
                    // Gaussian Blur to smooth noise
                    org.opencv.imgproc.Imgproc.GaussianBlur(grayMat, grayMat, org.opencv.core.Size(3.0, 3.0), 0.0)

                    // Adaptive Thresholding for sharp document scanning
                    org.opencv.imgproc.Imgproc.adaptiveThreshold(
                        grayMat,
                        binMat,
                        255.0,
                        org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        org.opencv.imgproc.Imgproc.THRESH_BINARY,
                        15,
                        10.0
                    )
                    org.opencv.imgproc.Imgproc.cvtColor(binMat, dstMat, org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA)
                    grayMat.release()
                    binMat.release()
                }
                "ENHANCED", "MAGIC", "MAGIC_COLOR" -> {
                    // Enhanced Magic Color mode: increase contrast & apply text sharpening filter
                    val enhancedMat = org.opencv.core.Mat()
                    org.opencv.core.Core.convertScaleAbs(srcMat, enhancedMat, 1.25, 10.0)

                    // Apply 3x3 sharpening kernel
                    val kernel = org.opencv.core.Mat(3, 3, org.opencv.core.CvType.CV_32F)
                    kernel.put(0, 0,
                        0.0, -0.5, 0.0,
                        -0.5, 3.0, -0.5,
                        0.0, -0.5, 0.0
                    )
                    org.opencv.imgproc.Imgproc.filter2D(enhancedMat, dstMat, -1, kernel)
                    enhancedMat.release()
                    kernel.release()
                }
                else -> { // ORIGINAL
                    srcMat.copyTo(dstMat)
                }
            }

            val resultBitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(dstMat, resultBitmap)

            srcMat.release()
            dstMat.release()

            return resultBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return source
        }
    }

    /**
     * Uses OpenCV Canny edge detection, morphing, and contour approximation to detect document quad boundaries.
     * Returns 4 normalized PointF coordinates (0.0 to 1.0) representing Top-Left, Top-Right, Bottom-Right, Bottom-Left.
     */
    fun detectDocumentQuad(bitmap: Bitmap): List<PointF> {
        try {
            val mat = org.opencv.core.Mat()
            org.opencv.android.Utils.bitmapToMat(bitmap, mat)

            val gray = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)

            val blur = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.GaussianBlur(gray, blur, org.opencv.core.Size(5.0, 5.0), 0.0)

            val canny = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.Canny(blur, canny, 50.0, 150.0)

            val kernel = org.opencv.imgproc.Imgproc.getStructuringElement(org.opencv.imgproc.Imgproc.MORPH_RECT, org.opencv.core.Size(3.0, 3.0))
            org.opencv.imgproc.Imgproc.dilate(canny, canny, kernel)

            val contours = ArrayList<org.opencv.core.MatOfPoint>()
            val hierarchy = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.findContours(
                canny,
                contours,
                hierarchy,
                org.opencv.imgproc.Imgproc.RETR_LIST,
                org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE
            )

            var maxArea = 0.0
            var bestQuad: org.opencv.core.MatOfPoint2f? = null

            for (contour in contours) {
                val c2f = org.opencv.core.MatOfPoint2f(*contour.toArray())
                val peri = org.opencv.imgproc.Imgproc.arcLength(c2f, true)
                val approx = org.opencv.core.MatOfPoint2f()
                org.opencv.imgproc.Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)

                if (approx.total() == 4L) {
                    val area = org.opencv.imgproc.Imgproc.contourArea(approx)
                    if (area > maxArea) {
                        maxArea = area
                        bestQuad = approx
                    }
                }
            }

            if (bestQuad != null && maxArea > (mat.cols() * mat.rows() * 0.05)) {
                val points = bestQuad.toArray()
                val width = mat.cols().toFloat()
                val height = mat.rows().toFloat()

                val normalizedPoints = points.map { PointF(it.x.toFloat() / width, it.y.toFloat() / height) }
                mat.release(); gray.release(); blur.release(); canny.release(); hierarchy.release(); kernel.release()
                return sortQuadPoints(normalizedPoints)
            }
            mat.release(); gray.release(); blur.release(); canny.release(); hierarchy.release(); kernel.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Default inset box fallback
        return listOf(
            PointF(0.05f, 0.05f),
            PointF(0.95f, 0.05f),
            PointF(0.95f, 0.95f),
            PointF(0.05f, 0.95f)
        )
    }

    /**
     * Combines OpenCV edge detection with ML Kit Text Recognition bounding boxes
     * to automatically detect document boundaries and calculate auto-crop quad points.
     */
    suspend fun detectAutoDocumentQuad(bitmap: Bitmap): List<PointF> {
        val openCvQuad = detectDocumentQuad(bitmap)
        
        // Check if OpenCV detected a distinct boundary rather than default fallback
        val isDefaultOpenCv = openCvQuad[0].x == 0.05f && openCvQuad[0].y == 0.05f && openCvQuad[2].x == 0.95f
        if (!isDefaultOpenCv) {
            return openCvQuad
        }

        // Try ML Kit Text Recognition bounds as intelligent fallback
        val mlKitQuad = com.example.api.MlKitOcrService.detectTextBoundsQuad(bitmap)
        return mlKitQuad ?: openCvQuad
    }

    private fun sortQuadPoints(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points
        val sortedBySum = points.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()

        val remaining = points.filter { it != tl && it != br }
        val tr = remaining.minByOrNull { it.y - it.x } ?: remaining[0]
        val bl = remaining.maxByOrNull { it.y - it.x } ?: remaining[1]

        return listOf(tl, tr, br, bl)
    }
}
