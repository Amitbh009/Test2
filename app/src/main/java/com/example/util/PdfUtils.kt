package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.DocBlock
import com.example.data.model.DrawingStroke
import com.example.data.model.PDFAnnotations
import com.example.data.model.StickyNote
import com.example.data.model.AreaHighlight
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    // Converts a list of DocBlocks in a Word Document into a vector PDF file (represented as ByteArray)
    fun generatePdfFromBlocks(context: Context, title: String, blocks: List<DocBlock>): ByteArray {
        val pdfDocument = PdfDocument()
        
        // Page specification: Standard A4 width = 595, height = 842 (in PostScript points, 1/72 inch)
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 54f
        val rightMargin = 54f
        val topMargin = 54f
        val bottomMargin = 54f
        val contentWidth = pageWidth - leftMargin - rightMargin
        
        var currentPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = topMargin

        // Draw header on the first page
        drawHeader(canvas, title, pageWidth)

        // Text format paints
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }

        for (block in blocks) {
            // Configure paint based on block type and styling properties
            val fontSize = when (block.type) {
                "TITLE" -> 26f
                "HEADING" -> 18f
                "SUBHEADING" -> 14f
                "QUOTE" -> 11f
                else -> 12f // PARAGRAPH & BULLET
            }
            textPaint.textSize = fontSize

            // Resolve Typeface (Bold/Italic)
            val styleFlag = when {
                block.isBold && block.isItalic -> Typeface.BOLD_ITALIC
                block.isBold -> Typeface.BOLD
                block.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            textPaint.typeface = Typeface.create(
                if (block.type == "QUOTE") Typeface.SERIF else Typeface.SANS_SERIF,
                styleFlag
            )

            // Parse text color
            try {
                textPaint.color = Color.parseColor(block.colorHex)
            } catch (e: Exception) {
                textPaint.color = Color.BLACK
            }

            // Paragraph alignment mapping
            val align = when (block.align) {
                "CENTER" -> Layout.Alignment.ALIGN_CENTER
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            // Handle Underline
            textPaint.isUnderlineText = block.isUnderline

            // Set block-specific padding & indent
            val indent = if (block.type == "BULLET") 15f else 0f
            val quoteExtraPadding = if (block.type == "QUOTE") 20f else 0f
            val blockWidth = contentWidth - indent - (quoteExtraPadding * 2)

            // Support decorative left-border drawing for QUOTEs
            if (block.type == "QUOTE") {
                val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.LTGRAY
                    strokeWidth = 3f
                }
                // We will draw vertical quote line after calculating static layout height
            }

            // Prepare layout for text wrapping
            val textToDraw = if (block.type == "BULLET") "•  ${block.text}" else block.text
            val staticLayout = StaticLayout.Builder.obtain(
                textToDraw, 0, textToDraw.length, textPaint, blockWidth.toInt()
            )
            .setAlignment(align)
            .setLineSpacing(2f, 1.1f)
            .build()

            val layoutHeight = staticLayout.height

            // Calculate spacing before block
            val spaceBefore = when (block.type) {
                "TITLE" -> 30f
                "HEADING" -> 20f
                "SUBHEADING" -> 15f
                else -> 10f
            }

            // Page overflow check
            if (currentY + spaceBefore + layoutHeight > pageHeight - bottomMargin) {
                // Draw footer before closing current page
                drawFooter(canvas, currentPageNum, pageWidth, pageHeight)
                pdfDocument.finishPage(page)

                // Start new page
                currentPageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = topMargin
            } else {
                currentY += spaceBefore
            }

            // Draw block background or left borders if needed
            if (block.type == "QUOTE") {
                val quoteLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textPaint.color
                    alpha = 100 // semi-transparent
                    strokeWidth = 3f
                }
                canvas.drawLine(
                    leftMargin + quoteExtraPadding - 8f,
                    currentY,
                    leftMargin + quoteExtraPadding - 8f,
                    currentY + layoutHeight,
                    quoteLinePaint
                )
            }

            // Render text
            canvas.save()
            canvas.translate(leftMargin + indent + quoteExtraPadding, currentY)
            staticLayout.draw(canvas)
            canvas.restore()

            // Update Y position
            currentY += layoutHeight
        }

        // Draw last page footer and finish
        drawFooter(canvas, currentPageNum, pageWidth, pageHeight)
        pdfDocument.finishPage(page)

        // Write output stream
        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        return outputStream.toByteArray()
    }

    private fun drawHeader(canvas: Canvas, title: String, pageWidth: Int) {
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val headerText = title.uppercase()
        canvas.drawText(headerText, 54f, 36f, headerPaint)
        
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(54f, 40f, pageWidth - 54f, 40f, linePaint)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, pageWidth: Int, pageHeight: Int) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(54f, pageHeight - 40f, pageWidth - 54f, pageHeight - 40f, linePaint)
        
        val footerText = "Page $pageNum"
        val textWidth = footerPaint.measureText(footerText)
        canvas.drawText(footerText, (pageWidth - textWidth) / 2f, pageHeight - 26f, footerPaint)
    }

    // Renders pages of a stored PDF file (represented as ByteArray) to a list of Bitmap images
    fun renderPdfToBitmaps(context: Context, pdfBytes: ByteArray): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var tempFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            tempFile = File.createTempFile("pdf_render_", ".pdf", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(pdfBytes) }

            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount

            for (i in 0 until count) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(i)
                    
                    // Keep scaling adaptive to prevent OutOfMemoryError on constrained memory instances
                    var scale = 2f
                    var bitmap: Bitmap? = null
                    while (scale >= 1f && bitmap == null) {
                        try {
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()
                            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        } catch (oom: OutOfMemoryError) {
                            System.gc()
                            scale -= 0.5f // Reduce resolution and try again
                        }
                    }

                    if (bitmap == null) {
                        // Maximum fallback
                        val width = page.width
                        val height = page.height
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    }

                    bitmap.eraseColor(Color.WHITE) // Fill with white
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                } finally {
                    try {
                        page?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                renderer?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return bitmaps
    }

    // Burn annotations (drawing strokes, sticky notes, highlights) directly onto the pages of the PDF, returning a new PDF ByteArray
    fun saveAnnotatedPdf(
        context: Context,
        originalPdfBytes: ByteArray,
        annotations: PDFAnnotations
    ): ByteArray {
        try {
            // First render the original pages so we can draw annotations on top
            val originalBitmaps = renderPdfToBitmaps(context, originalPdfBytes)
            if (originalBitmaps.isEmpty()) return originalPdfBytes

            val pdfDocument = PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            for (i in originalBitmaps.indices) {
                val pageBitmap = originalBitmaps[i]
                
                // Create mutable bitmap to draw on
                val mutableBitmap = pageBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                // Find annotations for this page
                val pageAnn = annotations.list.find { it.pageIndex == i }
                if (pageAnn != null) {
                    // 1. Draw Highlights
                    for (hl in pageAnn.highlights) {
                        try {
                            val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor(hl.colorHex)
                                style = Paint.Style.FILL
                            }
                            // Since standard canvas in PDF can be scaled differently, we scale coordinates based on bitmap dimensions
                            // Here we assume saved coordinates are relative (0 to 1) or inside target container coordinates.
                            // To be resilient, if coordinates are inside 0 to 1, we multiply by size.
                            val left = if (hl.x1 in 0f..1f) hl.x1 * canvas.width else hl.x1
                            val top = if (hl.y1 in 0f..1f) hl.y1 * canvas.height else hl.y1
                            val right = if (hl.x2 in 0f..1f) hl.x2 * canvas.width else hl.x2
                            val bottom = if (hl.y2 in 0f..1f) hl.y2 * canvas.height else hl.y2

                            canvas.drawRect(left, top, right, bottom, hlPaint)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2. Draw Inking Strokes
                    for (stroke in pageAnn.strokes) {
                        if (stroke.pointsX.size < 2) continue
                        try {
                            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor(stroke.colorHex)
                                strokeWidth = stroke.thickness * (canvas.width / 400f) // Scale stroke relatively
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }

                            for (p in 0 until stroke.pointsX.size - 1) {
                                val x1 = if (stroke.pointsX[p] in 0f..1f) stroke.pointsX[p] * canvas.width else stroke.pointsX[p]
                                val y1 = if (stroke.pointsY[p] in 0f..1f) stroke.pointsY[p] * canvas.height else stroke.pointsY[p]
                                val x2 = if (stroke.pointsX[p+1] in 0f..1f) stroke.pointsX[p+1] * canvas.width else stroke.pointsX[p+1]
                                val y2 = if (stroke.pointsY[p+1] in 0f..1f) stroke.pointsY[p+1] * canvas.height else stroke.pointsY[p+1]

                                canvas.drawLine(x1, y1, x2, y2, strokePaint)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 3. Draw Sticky Notes
                    for (note in pageAnn.notes) {
                        try {
                            val x = if (note.x in 0f..1f) note.x * canvas.width else note.x
                            val y = if (note.y in 0f..1f) note.y * canvas.height else note.y

                            // Draw a small sticky note box background
                            val noteBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor(note.colorHex)
                                style = Paint.Style.FILL
                                setShadowLayer(4f, 2f, 2f, Color.GRAY)
                            }
                            
                            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                textSize = canvas.width / 42f // Scaled text
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                            }

                            // Dynamic box sizing
                            val textPadding = 12f
                            val noteWidth = canvas.width * 0.28f
                            
                            val staticLayout = StaticLayout.Builder.obtain(
                                note.text, 0, note.text.length, textPaint, (noteWidth - textPadding * 2).toInt()
                            )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .build()

                            val noteHeight = staticLayout.height + textPadding * 2

                            canvas.save()
                            canvas.translate(x, y)
                            canvas.drawRoundRect(0f, 0f, noteWidth, noteHeight, 10f, 10f, noteBoxPaint)
                            canvas.translate(textPadding, textPadding)
                            staticLayout.draw(canvas)
                            canvas.restore()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Append page into PdfDocument
                val pageInfo = PdfDocument.PageInfo.Builder(pageBitmap.width, pageBitmap.height, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val destCanvas = page.canvas
                destCanvas.drawBitmap(mutableBitmap, 0f, 0f, paint)
                pdfDocument.finishPage(page)

                // Clean up bitmaps
                mutableBitmap.recycle()
                pageBitmap.recycle()
            }

            val outStream = ByteArrayOutputStream()
            pdfDocument.writeTo(outStream)
            pdfDocument.close()
            return outStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return originalPdfBytes
    }
}
