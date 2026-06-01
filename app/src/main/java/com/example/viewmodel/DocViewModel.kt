package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.DocBlock
import com.example.data.model.DocItem
import com.example.data.model.DocumentContent
import com.example.data.model.PageAnnotations
import com.example.data.model.PDFAnnotations
import com.example.data.model.DrawingStroke
import com.example.data.model.StickyNote
import com.example.data.model.AreaHighlight
import com.example.data.repository.DocRepository
import com.example.util.PdfUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID

class DocViewModel(private val repository: DocRepository) : ViewModel() {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val docContentAdapter = moshi.adapter(DocumentContent::class.java)
    private val pdfAnnotationsAdapter = moshi.adapter(PDFAnnotations::class.java)

    private var saveJob: kotlinx.coroutines.Job? = null

    // All documents observed in the UI
    val allDocuments = repository.allDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current operating Document State
    private val _currentDocument = MutableStateFlow<DocItem?>(null)
    val currentDocument: StateFlow<DocItem?> = _currentDocument.asStateFlow()

    // Title editing state
    private val _editTitle = MutableStateFlow("")
    val editTitle: StateFlow<String> = _editTitle.asStateFlow()

    // Active Word Document style block states
    private val _wordContent = MutableStateFlow(DocumentContent())
    val wordContent: StateFlow<DocumentContent> = _wordContent.asStateFlow()

    private val _activeBlockId = MutableStateFlow<String?>(null)
    val activeBlockId: StateFlow<String?> = _activeBlockId.asStateFlow()

    // Active PDF Annotations mapping state
    private val _pdfAnnotations = MutableStateFlow(PDFAnnotations())
    val pdfAnnotations: StateFlow<PDFAnnotations> = _pdfAnnotations.asStateFlow()

    // PDF Pages bitmaps cache
    private val _pdfBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val pdfBitmaps: StateFlow<List<Bitmap>> = _pdfBitmaps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Selection or annotation modes
    private val _editMode = MutableStateFlow("SELECT") // "SELECT" or "DRAW" or "HIGHLIGHT" or "NOTE"
    val editMode: StateFlow<String> = _editMode.asStateFlow()

    private val _brushColor = MutableStateFlow("#FFFF0000") // Default red pen
    val brushColor: StateFlow<String> = _brushColor.asStateFlow()

    private val _brushThickness = MutableStateFlow(6f)
    val brushThickness: StateFlow<Float> = _brushThickness.asStateFlow()

    fun updateTitle(newTitle: String) {
        _editTitle.value = newTitle
    }

    fun setEditMode(mode: String) {
        _editMode.value = mode
    }

    fun setBrushColor(colorHex: String) {
        _brushColor.value = colorHex
    }

    fun setBrushThickness(thickness: Float) {
        _brushThickness.value = thickness
    }

    fun setActiveBlockId(blockId: String?) {
        _activeBlockId.value = blockId
    }

    // Initialize/Create a brand new Word style document
    fun createNewWordDocument() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Standard preset blocks resembling Microsoft Word document initialization
            val defaultBlocks = listOf(
                DocBlock(
                    id = UUID.randomUUID().toString(),
                    type = "TITLE",
                    text = "Untitled Document",
                    align = "CENTER",
                    isBold = true
                ),
                DocBlock(
                    id = UUID.randomUUID().toString(),
                    type = "HEADING",
                    text = "1. Introduction",
                    isBold = true
                ),
                DocBlock(
                    id = UUID.randomUUID().toString(),
                    type = "PARAGRAPH",
                    text = "Welcome to your new document. Tap anywhere on any block to select it and update formatting. You can toggle text styles like Bold, Italic, and Underline, change layouts, and seamlessly export your masterpiece into high-fidelity PDF format."
                )
            )
            
            val contentJsonString = docContentAdapter.toJson(DocumentContent(defaultBlocks))
            val newDoc = DocItem(
                title = "New Document",
                contentJson = contentJsonString,
                type = "WORD"
            )
            val docId = repository.insertDocument(newDoc)
            loadDocument(docId.toInt(), null)
            _isLoading.value = false
        }
    }

    // Initialize/Create document from imported PDF stream
    fun createPDFDocumentFromStream(context: Context, fileName: String, inputStream: InputStream) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pdfBytes = inputStream.readBytes()
                val newDoc = DocItem(
                    title = fileName,
                    contentJson = "",
                    type = "PDF",
                    pdfBytes = pdfBytes,
                    annotationsJson = pdfAnnotationsAdapter.toJson(PDFAnnotations())
                )
                val docId = repository.insertDocument(newDoc)
                loadDocument(docId.toInt(), context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Create a demo pre-crafted annotated PDF document
    fun createDemoPdfDocument(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // We generate a beautiful base PDF using PdfUtils first
                val title = "Sample Report Guide"
                val defaultBlocks = listOf(
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "TITLE",
                        text = "Quarterly Business Report",
                        align = "CENTER",
                        isBold = true,
                        colorHex = "#FF0D47A1" // Dark blue
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "HEADING",
                        text = "Executive Summary",
                        isBold = true,
                        colorHex = "#FF1565C0"
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "PARAGRAPH",
                        text = "This report outlines key performance metrics and projections for the upcoming quarters. Visual designs and digital annotations can be overlaid on top of this generated statement."
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "QUOTE",
                        text = "Our primary focus is driven by expanding our document automation pipeline to maintain absolute consistency."
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "HEADING",
                        text = "Core Milestones",
                        isBold = true,
                        colorHex = "#FF1565C0"
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "BULLET",
                        text = "Deploy full PDF compilation support matching standard PostScript layouts."
                    ),
                    DocBlock(
                        id = UUID.randomUUID().toString(),
                        type = "BULLET",
                        text = "Enable instant canvas overlay with brush stroke inking inputs."
                    )
                )

                val originalPdfBytes = PdfUtils.generatePdfFromBlocks(context, title, defaultBlocks)
                
                // Set default annotations
                val defaultAnn = PDFAnnotations(
                    list = listOf(
                        PageAnnotations(
                            pageIndex = 0,
                            strokes = listOf(
                                DrawingStroke(
                                    pointsX = listOf(0.12f, 0.17f, 0.22f, 0.35f, 0.44f),
                                    pointsY = listOf(0.55f, 0.58f, 0.60f, 0.59f, 0.57f),
                                    colorHex = "#FFFF0000",
                                    thickness = 6f
                                )
                            ),
                            highlights = listOf(
                                AreaHighlight(
                                    id = UUID.randomUUID().toString(),
                                    x1 = 0.12f, y1 = 0.15f, x2 = 0.88f, y2 = 0.22f,
                                    colorHex = "#40FFFF00"
                                )
                            ),
                            notes = listOf(
                                StickyNote(
                                    id = UUID.randomUUID().toString(),
                                    text = "Needs review!",
                                    x = 0.68f, y = 0.35f,
                                    colorHex = "#FFFFEE70"
                                )
                            )
                        )
                    )
                )

                val newDoc = DocItem(
                    title = "Sample Report Guide",
                    contentJson = "",
                    type = "PDF",
                    pdfBytes = originalPdfBytes,
                    annotationsJson = pdfAnnotationsAdapter.toJson(defaultAnn)
                )
                
                val docId = repository.insertDocument(newDoc)
                loadDocument(docId.toInt(), context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Load active Document
    fun loadDocument(docId: Int, context: Context?) {
        saveJob?.cancel() // Cancel any pending save tasks before loading a different file
        
        // Recycle old page bitmaps instantly to avoid OOM or InputChannel memory exhaustion crashes
        val oldList = _pdfBitmaps.value
        _pdfBitmaps.value = emptyList()
        oldList.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }

        viewModelScope.launch {
            _isLoading.value = true
            val doc = repository.getDocumentById(docId)
            if (doc != null) {
                _currentDocument.value = doc
                _editTitle.value = doc.title
                
                if (doc.type == "WORD") {
                    val parsed = try {
                        docContentAdapter.fromJson(doc.contentJson) ?: DocumentContent()
                    } catch (e: Exception) {
                        DocumentContent()
                    }
                    _wordContent.value = parsed
                    _activeBlockId.value = parsed.blocks.firstOrNull()?.id
                } else {
                    // PDF Annotation mode
                    val parsed = try {
                        doc.annotationsJson?.let { pdfAnnotationsAdapter.fromJson(it) } ?: PDFAnnotations()
                    } catch (e: Exception) {
                        PDFAnnotations()
                    }
                    _pdfAnnotations.value = parsed
                    _wordContent.value = DocumentContent()
                    _activeBlockId.value = null
                    
                    // Decode bitmap caches
                    if (context != null && doc.pdfBytes != null) {
                        _pdfBitmaps.value = PdfUtils.renderPdfToBitmaps(context, doc.pdfBytes)
                    }
                }
            } else {
                _currentDocument.value = null
                _editTitle.value = ""
                _wordContent.value = DocumentContent()
                _pdfAnnotations.value = PDFAnnotations()
            }
            _isLoading.value = false
        }
    }

    // Word mode updates
    fun addBlock(type: String) {
        val blocks = _wordContent.value.blocks.toMutableList()
        val newId = UUID.randomUUID().toString()
        val defaultText = when (type) {
            "TITLE" -> "New Title"
            "HEADING" -> "New Heading"
            "SUBHEADING" -> "New Subheading"
            "BULLET" -> "Bullet Item text"
            "QUOTE" -> "This is an important quote to highlight."
            else -> "Enter some text here..."
        }
        
        val newBlock = DocBlock(
            id = newId,
            type = type,
            text = defaultText,
            align = "LEFT"
        )
        blocks.add(newBlock)
        
        _wordContent.value = DocumentContent(blocks)
        _activeBlockId.value = newId
        saveCurrentDoc(immediate = true)
    }

    fun updateBlockText(blockId: String, text: String) {
        val blocks = _wordContent.value.blocks.map { block ->
            if (block.id == blockId) block.copy(text = text) else block
        }
        _wordContent.value = DocumentContent(blocks)
        saveCurrentDoc(immediate = false)
    }

    fun updateBlockStyles(
        blockId: String,
        isBold: Boolean? = null,
        isItalic: Boolean? = null,
        isUnderline: Boolean? = null,
        align: String? = null,
        type: String? = null,
        colorHex: String? = null
    ) {
        val blocks = _wordContent.value.blocks.map { block ->
            if (block.id == blockId) {
                block.copy(
                    isBold = isBold ?: block.isBold,
                    isItalic = isItalic ?: block.isItalic,
                    isUnderline = isUnderline ?: block.isUnderline,
                    align = align ?: block.align,
                    type = type ?: block.type,
                    colorHex = colorHex ?: block.colorHex
                )
            } else {
                block
            }
        }
        _wordContent.value = DocumentContent(blocks)
        saveCurrentDoc(immediate = true)
    }

    fun removeBlock(blockId: String) {
        val current = _wordContent.value.blocks
        if (current.size <= 1) return // Keep at least one block
        
        val filtered = current.filter { it.id != blockId }
        _wordContent.value = DocumentContent(filtered)
        if (_activeBlockId.value == blockId) {
            _activeBlockId.value = filtered.first().id
        }
        saveCurrentDoc(immediate = true)
    }

    fun moveBlockUp(blockId: String) {
        val blocks = _wordContent.value.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index > 0) {
            val temp = blocks[index]
            blocks[index] = blocks[index - 1]
            blocks[index - 1] = temp
            _wordContent.value = DocumentContent(blocks)
            saveCurrentDoc(immediate = true)
        }
    }

    fun moveBlockDown(blockId: String) {
        val blocks = _wordContent.value.blocks.toMutableList()
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index >= 0 && index < blocks.size - 1) {
            val temp = blocks[index]
            blocks[index] = blocks[index + 1]
            blocks[index + 1] = temp
            _wordContent.value = DocumentContent(blocks)
            saveCurrentDoc(immediate = true)
        }
    }

    // PDF mode drawing / highlights / notes additions
    fun addStroke(pageIndex: Int, stroke: DrawingStroke) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        
        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val strokes = pageAnn.strokes.toMutableList().apply { add(stroke) }
            list[pageIdx] = pageAnn.copy(strokes = strokes)
        } else {
            list.add(PageAnnotations(pageIndex = pageIndex, strokes = listOf(stroke)))
        }
        
        _pdfAnnotations.value = PDFAnnotations(list)
        saveCurrentDoc(immediate = true)
    }

    fun addStickyNote(pageIndex: Int, text: String, x: Float, y: Float) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        
        val note = StickyNote(
            id = UUID.randomUUID().toString(),
            text = text,
            x = x,
            y = y
        )

        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val notes = pageAnn.notes.toMutableList().apply { add(note) }
            list[pageIdx] = pageAnn.copy(notes = notes)
        } else {
            list.add(PageAnnotations(pageIndex = pageIndex, notes = listOf(note)))
        }

        _pdfAnnotations.value = PDFAnnotations(list)
        saveCurrentDoc(immediate = true)
    }

    fun addHighlight(pageIndex: Int, x1: Float, y1: Float, x2: Float, y2: Float, colorHex: String) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        
        val highlight = AreaHighlight(
            id = UUID.randomUUID().toString(),
            x1 = x1, y1 = y1, x2 = x2, y2 = y2,
            colorHex = colorHex
        )

        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val highlights = pageAnn.highlights.toMutableList().apply { add(highlight) }
            list[pageIdx] = pageAnn.copy(highlights = highlights)
        } else {
            list.add(PageAnnotations(pageIndex = pageIndex, highlights = listOf(highlight)))
        }

        _pdfAnnotations.value = PDFAnnotations(list)
        saveCurrentDoc(immediate = true)
    }

    fun clearAnnotations(pageIndex: Int) {
        val list = _pdfAnnotations.value.list.map { pageAnn ->
            if (pageAnn.pageIndex == pageIndex) {
                pageAnn.copy(
                    strokes = emptyList(),
                    notes = emptyList(),
                    highlights = emptyList(),
                    textBlocks = emptyList()
                )
            } else {
                pageAnn
            }
        }
        _pdfAnnotations.value = PDFAnnotations(list)
        saveCurrentDoc(immediate = true)
    }

    fun addPdfTextBlock(
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 14f,
        colorHex: String = "#FF1F1F1F",
        isBold: Boolean = false,
        isItalic: Boolean = false,
        hasWhiteout: Boolean = false
    ) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        
        val newBlock = com.example.data.model.PdfTextBlock(
            id = UUID.randomUUID().toString(),
            text = text,
            x = x,
            y = y,
            fontSize = fontSize,
            colorHex = colorHex,
            isBold = isBold,
            isItalic = isItalic,
            hasWhiteout = hasWhiteout
        )

        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val textBlocks = (pageAnn.textBlocks ?: emptyList()).toMutableList().apply { add(newBlock) }
            list[pageIdx] = pageAnn.copy(textBlocks = textBlocks)
        } else {
            list.add(PageAnnotations(pageIndex = pageIndex, textBlocks = listOf(newBlock)))
        }

        _pdfAnnotations.value = PDFAnnotations(list)
        saveCurrentDoc(immediate = true)
    }

    fun updatePdfTextBlock(
        pageIndex: Int,
        blockId: String,
        text: String? = null,
        fontSize: Float? = null,
        colorHex: String? = null,
        isBold: Boolean? = null,
        isItalic: Boolean? = null,
        hasWhiteout: Boolean? = null,
        x: Float? = null,
        y: Float? = null
    ) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val updatedBlocks = (pageAnn.textBlocks ?: emptyList()).map { tb ->
                if (tb.id == blockId) {
                    tb.copy(
                        text = text ?: tb.text,
                        fontSize = fontSize ?: tb.fontSize,
                        colorHex = colorHex ?: tb.colorHex,
                        isBold = isBold ?: tb.isBold,
                        isItalic = isItalic ?: tb.isItalic,
                        hasWhiteout = hasWhiteout ?: tb.hasWhiteout,
                        x = x ?: tb.x,
                        y = y ?: tb.y
                    )
                } else tb
            }
            list[pageIdx] = pageAnn.copy(textBlocks = updatedBlocks)
            _pdfAnnotations.value = PDFAnnotations(list)
            saveCurrentDoc(immediate = false)
        }
    }

    fun removePdfTextBlock(pageIndex: Int, blockId: String) {
        val list = _pdfAnnotations.value.list.toMutableList()
        val pageIdx = list.indexOfFirst { it.pageIndex == pageIndex }
        if (pageIdx >= 0) {
            val pageAnn = list[pageIdx]
            val filtered = (pageAnn.textBlocks ?: emptyList()).filter { it.id != blockId }
            list[pageIdx] = pageAnn.copy(textBlocks = filtered)
            _pdfAnnotations.value = PDFAnnotations(list)
            saveCurrentDoc(immediate = true)
        }
    }

    // Save current active Doc to Room DB
    fun saveCurrentDoc(immediate: Boolean = false) {
        val current = _currentDocument.value ?: return
        saveJob?.cancel()

        val saveTask = suspend {
            val updatedDoc = if (current.type == "WORD") {
                val contentJsonStr = docContentAdapter.toJson(_wordContent.value)
                current.copy(
                    title = _editTitle.value,
                    contentJson = contentJsonStr,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                val annJsonStr = pdfAnnotationsAdapter.toJson(_pdfAnnotations.value)
                current.copy(
                    title = _editTitle.value,
                    annotationsJson = annJsonStr,
                    updatedAt = System.currentTimeMillis()
                )
            }
            repository.updateDocument(updatedDoc)
            _currentDocument.value = updatedDoc
        }

        if (immediate) {
            viewModelScope.launch {
                saveTask()
            }
        } else {
            saveJob = viewModelScope.launch {
                kotlinx.coroutines.delay(800) // 800ms debounce for high frequency actions like typing
                saveTask()
            }
        }
    }

    // Exports the document and compiles annotations into a shared PDF bytes array
    fun compileAndGetPdfBytes(context: Context): ByteArray? {
        val current = _currentDocument.value ?: return null
        return if (current.type == "WORD") {
            // Generate real PDF from Word list blocks
            PdfUtils.generatePdfFromBlocks(context, _editTitle.value, _wordContent.value.blocks)
        } else {
            // Merge annotations directly into standard document
            if (current.pdfBytes == null) return null
            PdfUtils.saveAnnotatedPdf(context, current.pdfBytes, _pdfAnnotations.value)
        }
    }

    // Delete a document from dashboard list
    fun deleteDocument(doc: DocItem) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _pdfBitmaps.value.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}

class DocViewModelFactory(private val repository: DocRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
