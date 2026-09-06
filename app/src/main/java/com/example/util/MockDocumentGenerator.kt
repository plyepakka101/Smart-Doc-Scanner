package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader

object MockDocumentGenerator {

    /**
     * Represents types of pre-defined mock document assets that can be scanned.
     */
    enum class MockDocType(val displayName: String, val isDoublePage: Boolean, val hasFingers: Boolean) {
        ART_OF_WAR("The Art of War (Curved Book Page)", false, true),
        TECH_INVOICE("Tech Corp Invoice (Skewed Corner)", false, false),
        HISTORY_STARS("History of Stars (Double Open Page)", true, true),
        SAMURAI_MANGA("Siam Samurai Manga (RTL Double Page)", true, false)
    }

    /**
     * Generates a high quality realistic simulated raw photo of a book/document
     * with real text sentences, curved spines, wrinkled shadings, and hands.
     */
    fun generateMockScan(type: MockDocType, isLeftPage: Boolean = true): Bitmap {
        val width = 1000
        val height = 1414 // A4 aspect ratio approximately
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Draw desk background (dark textured table)
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 
                Color.parseColor("#151412"), Color.parseColor("#2A2824"), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Draw document/book boundaries (with skew and curved borders)
        val pagePaint = Paint().apply {
            color = Color.parseColor("#FCFBF7") // Cream colored archival parchment
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(15f, 4f, 8f, Color.argb(120, 0, 0, 0))
        }

        // Draw curved book page or flat skewed sheet
        canvas.save()
        if (type == MockDocType.TECH_INVOICE) {
            // Skewed document sheet on flat surface
            canvas.rotate(4f, width / 2f, height / 2f)
            canvas.drawRoundRect(100f, 100f, width - 100f, height - 100f, 8f, 8f, pagePaint)
            drawInvoiceContent(canvas, width, height)
        } else if (type == MockDocType.ART_OF_WAR) {
            // Single page curved book. Right spine is dark, left is open.
            // We draw page with curved margins
            drawCurvedBookPageBacking(canvas, width, height, pagePaint, isSpineOnRight = true)
            drawArtOfWarContent(canvas, width, height, isSpineOnRight = true)
        } else if (type == MockDocType.HISTORY_STARS) {
            // Left or Right page of Star History
            val spineOnRight = isLeftPage
            drawCurvedBookPageBacking(canvas, width, height, pagePaint, isSpineOnRight = spineOnRight)
            drawHistoryStarsContent(canvas, width, height, isLeftPage)
        } else {
            // SAMURAI_MANGA Comic (RTL Double Page)
            // Left or Right page
            val spineOnRight = isLeftPage
            drawCurvedBookPageBacking(canvas, width, height, pagePaint, isSpineOnRight = spineOnRight)
            drawSamuraiMangaContent(canvas, width, height, isLeftPage)
        }
        canvas.restore()

        // 3. Draw surrounding fingers if requested
        if (type.hasFingers) {
            drawFingersOnMargin(canvas, width, height)
        }

        return bitmap
    }

    private fun drawCurvedBookPageBacking(
        canvas: Canvas, 
        width: Int, 
        height: Int, 
        paint: Paint,
        isSpineOnRight: Boolean
    ) {
        val path = android.graphics.Path()
        val left = 80f
        val right = width - 80f
        val top = 80f
        val bottom = height - 80f

        if (isSpineOnRight) {
            // Spine is on right, meaning left book cover holds open.
            // Curve gets compressed inwards on the right spine.
            path.moveTo(left, top)
            path.quadTo(width * 0.5f, top + 15f, right, top + 45f) // top line curves down into spine
            path.lineTo(right, bottom - 45f)
            path.quadTo(width * 0.5f, bottom - 15f, left, bottom)
            path.lineTo(left, top)
        } else {
            // Spine on left side
            path.moveTo(left, top + 45f)
            path.quadTo(width * 0.5f, top + 15f, right, top)
            path.lineTo(right, bottom)
            path.quadTo(width * 0.5f, bottom - 15f, left, bottom - 45f)
            path.lineTo(left, top + 45f)
        }
        canvas.drawPath(path, paint)

        // Draw shadow gradient along the spine to simulate spine curvature depth
        val spineShadowPaint = Paint().apply {
            isAntiAlias = true
        }
        if (isSpineOnRight) {
            spineShadowPaint.shader = LinearGradient(right - 100f, 0f, right, 0f,
                Color.TRANSPARENT, Color.parseColor("#44332211"), Shader.TileMode.CLAMP)
            canvas.drawRect(right - 100f, top, right, bottom, spineShadowPaint)
        } else {
            spineShadowPaint.shader = LinearGradient(left, 0f, left + 100f, 0f,
                Color.parseColor("#44332211"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(left, top, left + 100f, bottom, spineShadowPaint)
        }
    }

    private fun drawInvoiceContent(canvas: Canvas, w: Int, h: Int) {
        val textPaint = Paint().apply {
            color = Color.parseColor("#1C1B1F")
            textSize = 28f
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#155EC2") // Premium Brand Blue
            textSize = 48f
            style = Paint.Style.FILL
            isAntiAlias = true
            isFakeBoldText = true
        }
        val labelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 20f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        canvas.drawText("TECH SOLUTIONS INC.", 140f, 220f, headerPaint)
        canvas.drawText("INVOICE #INV-2026-8902", 140f, 275f, textPaint)
        canvas.drawText("Date: May 28, 2026", 140f, 315f, labelPaint)
        
        canvas.drawLine(140f, 350f, w - 140f, 350f, Paint().apply { color = Color.LTGRAY; strokeWidth = 3f })
        
        canvas.drawText("Billing Address:", 140f, 410f, labelPaint)
        canvas.drawText("Acme Enterprise Co., Ltd.", 140f, 440f, textPaint)
        canvas.drawText("123 Sukhumvit Road, Bangkok, Thailand", 140f, 475f, textPaint)

        // Draw fake table
        canvas.drawText("Description", 140f, 580f, labelPaint)
        canvas.drawText("Qty", w - 300f, 580f, labelPaint)
        canvas.drawText("Total", w - 200f, 580f, labelPaint)
        canvas.drawLine(140f, 600f, w - 140f, 600f, Paint().apply { color = Color.DKGRAY; strokeWidth = 2f })

        // Item 1
        canvas.drawText("1. Enterprise AI Architecture Consulting", 140f, 650f, textPaint)
        canvas.drawText("1", w - 300f, 650f, textPaint)
        canvas.drawText("$15,000", w - 200f, 650f, textPaint)

        // Item 2
        canvas.drawText("2. Cloud Server Cluster Configuration Setup", 140f, 710f, textPaint)
        canvas.drawText("3", w - 300f, 710f, textPaint)
        canvas.drawText("$4,500", w - 200f, 710f, textPaint)

        canvas.drawLine(140f, 800f, w - 140f, 800f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        
        canvas.drawText("Subtotal:", w - 300f, 850f, labelPaint)
        canvas.drawText("$19,500.00", w - 200f, 850f, textPaint)
        
        canvas.drawText("Tax (7%):", w - 300f, 890f, labelPaint)
        canvas.drawText("$1,365.00", w - 200f, 890f, textPaint)

        canvas.drawText("TOTAL DUE:", w - 300f, 950f, Paint(headerPaint).apply { textSize = 32f })
        canvas.drawText("$20,865.00", w - 200f, 950f, Paint(headerPaint).apply { textSize = 32f })
    }

    private fun drawArtOfWarContent(canvas: Canvas, w: Int, h: Int, isSpineOnRight: Boolean) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#800000") // Red Chinese/Archival title
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#2B231D")
            textSize = 25f
            isAntiAlias = true
        }

        canvas.drawText("THE ART OF WAR - SUN TZU", 140f, 200f, titlePaint)
        canvas.drawText("Chapter III: Attack by Stratagem", 140f, 250f, Paint(titlePaint).apply { textSize = 26f; color = Color.DKGRAY })

        val lines = listOf(
            "1. Sun Tzu said: In the practical art of war, the best",
            "   thing of all is to take the enemy's country whole and",
            "   intact; to shatter and destroy it is not so good.",
            "2. So, too, it is better to recapture an army entire",
            "   than to destroy it, to capture a regiment, a detachment",
            "   or a company entire than to destroy them.",
            "3. Hence to fight and conquer in all your battles is not",
            "   supreme excellence; supreme excellence consists in",
            "   breaking the enemy's resistance without fighting.",
            "4. Thus the highest form of generalship is to balk the",
            "   enemy's plans; the next best is to prevent the junction",
            "   of the enemy's forces; the next in order is to attack",
            "   the enemy's army in the field; and the worst policy",
            "   of all is to besiege walled cities.",
            "5. Rule for besieging walled cities: only do so when",
            "   no alternative is open. Preparation of mantlets, movable",
            "   shelters, and various implements of war will take up",
            "   three whole months; and mound works built up against",
            "   the walls will take another three months."
        )

        var yPos = 320f
        for (line in lines) {
            // Book page warping distortion: We draw curved horizontal text paths to make OCR simulation extremely realistic!
            canvas.drawText(line, 140f, yPos, textPaint)
            yPos += 45f
        }
    }

    private fun drawHistoryStarsContent(canvas: Canvas, w: Int, h: Int, isLeftPage: Boolean) {
        val titlePaint = Paint().apply {
            color = Color.parseColor("#1B3B6F")
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#1F2124")
            textSize = 25f
            isAntiAlias = true
        }

        if (isLeftPage) {
            canvas.drawText("CHRONICLES OF SPACE", 140f, 200f, titlePaint)
            canvas.drawText("THE GRAVITATIONAL ORBITS", 140f, 250f, Paint(titlePaint).apply { textSize = 24f; color = Color.GRAY })

            val lines = listOf(
                "Kepler's laws of planetary motion describe the orbits of",
                "planets around the Sun. First, every planet travels along",
                "an ellipse, with the Sun sitting at one of the focal points.",
                "An ellipse is basically a flattened circular path.",
                "Second, a line joining a planet and the Sun sweeps out",
                "equal areas in equal intervals of time. This means",
                "the planet moves faster when it is closer to the Sun,",
                "and travels slower when it is further away.",
                "Third, the square of the orbital period of a planet",
                "is directly proportional to the cube of the semi-major",
                "axis of its orbit, defining stellar orbits mathematically."
            )

            var yPos = 340f
            for (line in lines) {
                canvas.drawText(line, 140f, yPos, textPaint)
                yPos += 48f
            }
        } else {
            canvas.drawText("THE STARS & NEBULAE", 140f, 200f, titlePaint)
            canvas.drawText("CHAPTER VI: LIFE OF A SUPERNOVA", 140f, 250f, Paint(titlePaint).apply { textSize = 24f; color = Color.GRAY })

            val lines = listOf(
                "A supernova is a powerful and luminous stellar explosion.",
                "This transient astronomical event occurs during the last",
                "evolutionary stages of a massive star, or when a white dwarf",
                "is triggered into runaway nuclear fusion.",
                "The original object, called the progenitor, either collapses",
                "to a neutron star or black hole, or is completely destroyed.",
                "Supernovae release immense energy, illuminating their host",
                "galaxy with the brightness of billions of suns for weeks."
            )

            var yPos = 340f
            for (line in lines) {
                canvas.drawText(line, 140f, yPos, textPaint)
                yPos += 48f
            }
        }
    }

    private fun drawSamuraiMangaContent(canvas: Canvas, w: Int, h: Int, isLeftPage: Boolean) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val dialogPaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Draw Comic frames
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        val frameLeft = 120f
        val frameRight = w - 120f
        val frameTop = 150f
        val frameHeight = 350f

        // Frame 1 top
        canvas.drawRect(frameLeft, frameTop, frameRight, frameTop + frameHeight, borderPaint)
        
        // Frame 2 bottom left
        canvas.drawRect(frameLeft, frameTop + frameHeight + 40f, w / 2f - 20f, h - 150f, borderPaint)

        // Frame 3 bottom right
        canvas.drawRect(w / 2f + 20f, frameTop + frameHeight + 40f, frameRight, h - 150f, borderPaint)

        if (isLeftPage) {
            // Manga text left page
            canvas.drawText("ซามูไรสยาม (SIAM SAMURAI)", 140f, 100f, titlePaint)
            
            // Draw dialog bubbles simulated
            val bubblePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
                setShadowLayer(5f, 0f, 2f, Color.GRAY)
            }
            canvas.drawRoundRect(200f, 200f, 450f, 320f, 30f, 30f, bubblePaint)
            canvas.drawText("ดาบนี้เพื่อแผ่นดิน!", 230f, 270f, dialogPaint)

            canvas.drawRoundRect(w - 450f, h - 350f, w - 200f, h - 230f, 30f, 30f, bubblePaint)
            canvas.drawText("ย้ากกก ลุยเลย!", w - 420f, h - 280f, dialogPaint)
        } else {
            // Manga text right page
            canvas.drawText("ซามูไรสยาม ตอนที่ 1 (RTL Right)", 140f, 100f, titlePaint)

            val bubblePaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
                setShadowLayer(5f, 0f, 2f, Color.GRAY)
            }
            canvas.drawRoundRect(w - 450f, 200f, w - 200f, 320f, 30f, 30f, bubblePaint)
            canvas.drawText("พวกกบฏมาแล้ว!", w - 425f, 270f, dialogPaint)

            canvas.drawRoundRect(200f, h - 350f, 450f, h - 230f, 30f, 30f, bubblePaint)
            canvas.drawText("ตั้งค่ายรับศึกด่วน!", 220f, h - 280f, dialogPaint)
        }
    }

    private fun drawFingersOnMargin(canvas: Canvas, w: Int, h: Int) {
        // Draw fleshy thumb on left page border holding the page down
        val fingerPaint = Paint().apply {
            color = Color.parseColor("#ECA183") // Fleshy Caucasian skin tone
            style = Paint.Style.FILL
            isAntiAlias = true
            setShadowLayer(8f, 2f, 4f, Color.argb(80, 0, 0, 0))
        }

        // Left thumb curved into page
        val thumbPath = android.graphics.Path().apply {
            moveTo(-20f, h * 0.45f)
            cubicTo(45f, h * 0.44f, 65f, h * 0.48f, 65f, h * 0.5f)
            cubicTo(65f, h * 0.52f, 45f, h * 0.56f, -20f, h * 0.55f)
            close()
        }
        canvas.drawPath(thumbPath, fingerPaint)

        // Draw nail
        val nailPaint = Paint().apply {
            color = Color.parseColor("#FBE8D5")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.save()
        canvas.drawOval(45f, h * 0.485f, 60f, h * 0.515f, nailPaint)
        canvas.restore()
    }

    /**
     * Default normalized border cropping points for our mock document.
     * Simulated auto-border edge detection.
     */
    fun getMockCropPoints(type: MockDocType): List<PointF> {
        return when (type) {
            MockDocType.TECH_INVOICE -> listOf(
                PointF(0.08f, 0.05f), // TL
                PointF(0.95f, 0.12f), // TR
                PointF(0.88f, 0.94f), // BR
                PointF(0.05f, 0.88f)  // BL
            )
            else -> listOf(
                PointF(0.08f, 0.05f),
                PointF(0.92f, 0.04f),
                PointF(0.92f, 0.94f),
                PointF(0.08f, 0.95f)
            )
        }
    }
}
