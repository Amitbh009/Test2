package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DocBlock(
    val id: String,
    val type: String, // "TITLE", "HEADING", "PARAGRAPH", "BULLET", "QUOTE"
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val align: String = "LEFT", // "LEFT", "CENTER", "RIGHT"
    val colorHex: String = "#FF1F1F1F" // Default deep dark text
)

@JsonClass(generateAdapter = true)
data class DocumentContent(
    val blocks: List<DocBlock> = emptyList()
)

// PDF Annotations
@JsonClass(generateAdapter = true)
data class DrawingStroke(
    val pointsX: List<Float>,
    val pointsY: List<Float>,
    val colorHex: String,
    val thickness: Float
)

@JsonClass(generateAdapter = true)
data class StickyNote(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val colorHex: String = "#FFFFEE70" // Yellow sticky note
)

@JsonClass(generateAdapter = true)
data class AreaHighlight(
    val id: String,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val colorHex: String = "#50FFFF00" // Alpha + yellow
)

@JsonClass(generateAdapter = true)
data class PdfTextBlock(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 14f,
    val colorHex: String = "#FF1F1F1F",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val hasWhiteout: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PageAnnotations(
    val pageIndex: Int,
    val strokes: List<DrawingStroke> = emptyList(),
    val notes: List<StickyNote> = emptyList(),
    val highlights: List<AreaHighlight> = emptyList(),
    val textBlocks: List<PdfTextBlock> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PDFAnnotations(
    val list: List<PageAnnotations> = emptyList()
)
