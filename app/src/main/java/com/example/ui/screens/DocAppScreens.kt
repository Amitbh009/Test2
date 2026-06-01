package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DocBlock
import com.example.data.model.DocItem
import com.example.data.model.DrawingStroke
import com.example.data.model.PageAnnotations
import com.example.viewmodel.DocViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppNavigation(viewModel: DocViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<String>("DASHBOARD") }
    var selectedDocId by remember { mutableStateOf(-1) }

    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()

    // Handle screen transitions based on load state or state flows
    LaunchedEffect(currentDoc) {
        if (currentDoc != null) {
            currentScreen = if (currentDoc?.type == "WORD") "WORD_EDITOR" else "PDF_ANNOTATOR"
        } else {
            currentScreen = "DASHBOARD"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                "DASHBOARD" -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToDoc = { id, isWord ->
                        selectedDocId = id
                        viewModel.loadDocument(id, if (isWord) null else context)
                    }
                )
                "WORD_EDITOR" -> WordEditorScreen(
                    viewModel = viewModel,
                    onExit = {
                        viewModel.saveCurrentDoc(immediate = true)
                        viewModel.loadDocument(-1, null) // reset
                    }
                )
                "PDF_ANNOTATOR" -> PdfAnnotatorScreen(
                    viewModel = viewModel,
                    onExit = {
                        viewModel.saveCurrentDoc(immediate = true)
                        viewModel.loadDocument(-1, null) // reset
                    }
                )
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DocViewModel,
    onNavigateToDoc: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    // PDF Importer launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                val stream = cr.openInputStream(uri)
                if (stream != null) {
                    val cursor = cr.query(uri, null, null, null, null)
                    var name = "Imported Document.pdf"
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) name = it.getString(nameIndex)
                        }
                    }
                    viewModel.createPDFDocumentFromStream(context, name, stream)
                    Toast.makeText(context, "Loaded PDF: $name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DocuPDF Workspace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Professional Document Workspace",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        viewModel.createDemoPdfDocument(context)
                        Toast.makeText(context, "Loaded Interactive Report Guide!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = "Load Demo Document",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createNewWordDocument() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_document_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Document")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Document", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // Document Selector Tools Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Connect Tools",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Create Word Doc Card Link
                        OutlinedCard(
                            onClick = { viewModel.createNewWordDocument() },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFE3F2FD), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF1976D2),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("New Word Doc", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Rich layouts", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }

                        // Upload Raw PDF Link
                        OutlinedCard(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFFFEBEE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DriveFileRenameOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Annotate PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Draw & Highlight", fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // Search and List heading
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your files...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            Text(
                text = "Recent Handcrafted Files",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredDocs = documents.filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }

                if (filteredDocs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Your digital workspace is currently raw",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create a Word style PDF from scratch, import a local file, or experience the demo system guide using the bulb button!",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DashboardItemCard(
                                doc = doc,
                                onClick = { onNavigateToDoc(doc.id, doc.type == "WORD") },
                                onDelete = { viewModel.deleteDocument(doc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardItemCard(
    doc: DocItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val dateString = formatter.format(Date(doc.updatedAt))

    val isWord = doc.type == "WORD"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("document_card_${doc.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isWord) Color(0xFFE3F2FD) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isWord) Icons.Filled.Description else Icons.Filled.BorderColor,
                    contentDescription = null,
                    tint = if (isWord) Color(0xFF1976D2) else Color(0xFFD32F2F),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Chip label
                    Text(
                        text = if (isWord) "WORD" else "PDF annotation",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWord) Color(0xFF1976D2) else Color(0xFFD32F2F),
                        modifier = Modifier
                            .background(
                                color = if (isWord) Color(0xFFE3F2FD) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateString,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete File",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Document?") },
            text = { Text("This will permanently remove '${doc.title}'. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// 2. WORD DOCUMENT EDITOR SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    viewModel: DocViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val editTitle by viewModel.editTitle.collectAsStateWithLifecycle()
    val wordContent by viewModel.wordContent.collectAsStateWithLifecycle()
    val activeBlockId by viewModel.activeBlockId.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val activeBlock = wordContent.blocks.find { it.id == activeBlockId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = editTitle,
                        onValueChange = {
                            viewModel.updateTitle(it)
                            viewModel.saveCurrentDoc()
                        },
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export/Share PDF button
                    Button(
                        onClick = {
                            val pdfBytes = viewModel.compileAndGetPdfBytes(context)
                            if (pdfBytes != null) {
                                sharePdfBytes(context, editTitle, pdfBytes)
                            } else {
                                Toast.makeText(context, "Error compiling document to PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF2F4F7)) // A soft office-colored background
        ) {
            // FORMAT BAR (Microsoft Word Style Toolbar)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Text Type Style Dropped Trigger Selector
                        if (activeBlock != null) {
                            var showTypeMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { showTypeMenu = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = activeBlock.type,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = showTypeMenu,
                                    onDismissRequest = { showTypeMenu = false }
                                ) {
                                    listOf("TITLE", "HEADING", "SUBHEADING", "PARAGRAPH", "BULLET", "QUOTE").forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type, fontSize = 12.sp) },
                                            onClick = {
                                                viewModel.updateBlockStyles(activeBlock.id, type = type)
                                                showTypeMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp))

                        // 2. Formatting Toggles (Bold, Italic, Underlined)
                        if (activeBlock != null) {
                            IconToggleButton(
                                checked = activeBlock.isBold,
                                onCheckedChange = { viewModel.updateBlockStyles(activeBlock.id, isBold = it) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeBlock.isBold) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            ) {
                                Icon(Icons.Filled.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
                            }

                            IconToggleButton(
                                checked = activeBlock.isItalic,
                                onCheckedChange = { viewModel.updateBlockStyles(activeBlock.id, isItalic = it) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeBlock.isItalic) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            ) {
                                Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
                            }

                            IconToggleButton(
                                checked = activeBlock.isUnderline,
                                onCheckedChange = { viewModel.updateBlockStyles(activeBlock.id, isUnderline = it) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeBlock.isUnderline) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            ) {
                                Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline", modifier = Modifier.size(18.dp))
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp))

                            // 3. Alignment Toggles
                            listOf("LEFT", "CENTER", "RIGHT").forEach { alignment ->
                                val selected = activeBlock.align == alignment
                                val icon = when (alignment) {
                                    "CENTER" -> Icons.Filled.FormatAlignCenter
                                    "RIGHT" -> Icons.AutoMirrored.Filled.FormatAlignRight
                                    else -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                }
                                IconButton(
                                    onClick = { viewModel.updateBlockStyles(activeBlock.id, align = alignment) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                ) {
                                    Icon(icon, contentDescription = alignment, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Row for Block structural operations & colors
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Block Colors Swatches
                        if (activeBlock != null) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Format Color: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                listOf(
                                    "#FF1F1F1F" to Color(0xFF1F1F1F), // Dark
                                    "#FF1565C0" to Color(0xFF1565C0), // Blue
                                    "#FF2E7D32" to Color(0xFF2E7D32), // Green
                                    "#FFC62828" to Color(0xFFC62828), // Crimson
                                    "#FF6A1B9A" to Color(0xFF6A1B9A)  // Purple
                                ).forEach { (hex, color) ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (activeBlock.colorHex == hex) 2.dp else 1.dp,
                                                color = if (activeBlock.colorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.updateBlockStyles(activeBlock.id, colorHex = hex) }
                                    )
                                }
                            }
                        }

                        // Block Order Tools (Up, Down, Delete)
                        if (activeBlock != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { viewModel.moveBlockUp(activeBlock.id) },
                                    modifier = Modifier.size(28.dp),
                                    enabled = wordContent.blocks.indexOfFirst { it.id == activeBlock.id } > 0
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.moveBlockDown(activeBlock.id) },
                                    modifier = Modifier.size(28.dp),
                                    enabled = wordContent.blocks.indexOfFirst { it.id == activeBlock.id } < wordContent.blocks.size - 1
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.removeBlock(activeBlock.id) },
                                    modifier = Modifier.size(28.dp),
                                    enabled = wordContent.blocks.size > 1
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Block", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // PAGE WORKSPACE (White sheets representation of standard document layouts)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    // Title Header Placeholder
                    Text(
                        text = "WORD DOCUMENT PAGE - MARGIN CONTROLS (A4 STANDARD)",
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    // Active blocks rendering
                    wordContent.blocks.forEachIndexed { index, block ->
                        EditableBlockRow(
                            block = block,
                            isActive = block.id == activeBlockId,
                            onFocus = { viewModel.setActiveBlockId(block.id) },
                            onTextChanged = { viewModel.updateBlockText(block.id, it) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Block inserters helper ribbon
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Word Layout Block Builder",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Heading" to "HEADING",
                                    "Paragraph" to "PARAGRAPH",
                                    "Bullet List" to "BULLET",
                                    "Blockquote" to "QUOTE"
                                ).forEach { (label, type) ->
                                    Button(
                                        onClick = { viewModel.addBlock(type) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    ) {
                                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditableBlockRow(
    block: DocBlock,
    isActive: Boolean,
    onFocus: () -> Unit,
    onTextChanged: (String) -> Unit
) {
    val fontSize = when (block.type) {
        "TITLE" -> 24.sp
        "HEADING" -> 18.sp
        "SUBHEADING" -> 14.sp
        "QUOTE" -> 12.sp
        else -> 12.sp // PARAGRAPH, BULLET
    }

    val style = when (block.type) {
        "QUOTE" -> FontStyle.Italic
        else -> FontStyle.Normal
    }

    val weight = when {
        block.isBold || block.type == "TITLE" || block.type == "HEADING" -> FontWeight.Bold
        else -> FontWeight.Normal
    }

    val decoration = if (block.isUnderline) {
        androidx.compose.ui.text.style.TextDecoration.Underline
    } else {
        androidx.compose.ui.text.style.TextDecoration.None
    }

    val align = when (block.align) {
        "CENTER" -> TextAlign.Center
        "RIGHT" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val color = try {
        Color(android.graphics.Color.parseColor(block.colorHex))
    } catch (e: Exception) {
        Color.Black
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isActive) Color(0xFFE3F2FD).copy(alpha = 0.3f) else Color.Transparent
            )
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) Color(0xFF1976D2).copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable { onFocus() }
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .testTag("editable_row_${block.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (block.type == "BULLET") {
                Text(
                    text = "• ",
                    fontSize = fontSize,
                    fontWeight = weight,
                    color = color,
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else if (block.type == "QUOTE") {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(color.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            BasicTextField(
                value = block.text,
                onValueChange = { onTextChanged(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                textStyle = TextStyle(
                    fontSize = fontSize,
                    fontWeight = weight,
                    fontStyle = style,
                    textDecoration = decoration,
                    textAlign = align,
                    color = color,
                    lineHeight = if (block.type == "PARAGRAPH") 18.sp else 24.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
            
            if (isActive) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterVertically),
                    tint = Color(0xFF1976D2)
                )
            }
        }
    }
}


// ==========================================
// 3. PDF ANNOTATOR SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotatorScreen(
    viewModel: DocViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val editTitle by viewModel.editTitle.collectAsStateWithLifecycle()
    val annotations by viewModel.pdfAnnotations.collectAsStateWithLifecycle()
    val pdfBitmaps by viewModel.pdfBitmaps.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val brushThickness by viewModel.brushThickness.collectAsStateWithLifecycle()

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteDialogCoords by remember { mutableStateOf(Offset(0f, 0f)) }
    var notePageIndex by remember { mutableStateOf(0) }
    var noteTextQuery by remember { mutableStateOf("") }

    var showAddTextDialog by remember { mutableStateOf(false) }
    var textDialogCoords by remember { mutableStateOf(Offset(0f, 0f)) }
    var textPageIndex by remember { mutableStateOf(0) }
    var typedTextQuery by remember { mutableStateOf("") }
    var typedTextSize by remember { mutableStateOf(14f) }
    var typedTextColorHex by remember { mutableStateOf("#FF1F1F1F") }
    var typedBold by remember { mutableStateOf(false) }
    var typedItalic by remember { mutableStateOf(false) }
    var typedWhiteout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = editTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.clearAnnotations(0)
                            Toast.makeText(context, "Cleared current annotations", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Clear Markup", color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            viewModel.saveCurrentDoc(immediate = true)
                            val pdfBytes = viewModel.compileAndGetPdfBytes(context)
                            if (pdfBytes != null) {
                                sharePdfBytes(context, editTitle, pdfBytes)
                            } else {
                                Toast.makeText(context, "Error printing annotations to PDF", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF2C2C2C)) // Dark presentation PDF canvas backdrop
        ) {
            // FLOATING TOOLBAR (Drawing settings)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp),
                color = Color(0xFFF7F7F7)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Modes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "PAN" to Icons.Filled.PanTool,
                                "DRAW" to Icons.Filled.Draw,
                                "HIGHLIGHT" to Icons.Filled.BorderColor,
                                "NOTE" to Icons.AutoMirrored.Filled.StickyNote2,
                                "CONTENT" to Icons.Filled.Edit
                            ).forEach { (mode, icon) ->
                                val selected = editMode == mode
                                TextButton(
                                    onClick = { viewModel.setEditMode(mode) },
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(icon, contentDescription = mode, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = when (mode) {
                                            "PAN" -> "Pan"
                                            "DRAW" -> "Pen"
                                            "HIGHLIGHT" -> "Marker"
                                            "NOTE" -> "Comment"
                                            else -> "Content"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Help Icon Info
                        IconButton(onClick = {
                            Toast.makeText(context, "Guide:\n• Pen: Draw Ink lines.\n• Marker: Swipe to highlight.\n• Note: Tap to drop Sticky comments.", Toast.LENGTH_LONG).show()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Brush / Marker Styling details (Expand if in Drawing/Highlight/Note mode)
                    if (editMode != "PAN") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Colors Row
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    "#FFFF0000" to Color.Red,
                                    "#FFFFFF00" to Color.Yellow,
                                    "#FF00C853" to Color(0xFF00C853), // Green
                                    "#FF00B0FF" to Color(0xFF00B0FF), // Blue
                                    "#FF000000" to Color.Black
                                ).forEach { (hex, tintHex) ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(tintHex)
                                            .clickable { viewModel.setBrushColor(hex) }
                                            .border(
                                                width = if (brushColor == hex) 2.dp else 0.dp,
                                                color = if (brushColor == hex) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            // Thickness Slider slider item
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(180.dp)
                            ) {
                                Text("Size: ", fontSize = 10.sp, color = Color.DarkGray)
                                Slider(
                                    value = brushThickness,
                                    onValueChange = { viewModel.setBrushThickness(it) },
                                    valueRange = 2f..30f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${brushThickness.toInt()}px",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            // PDF RENDER VIEW
            if (pdfBitmaps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Rendering Document vector layers...", color = Color.White, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(pdfBitmaps.size) { pageIndex ->
                        val bitmap = pdfBitmaps[pageIndex]
                        PdfPageWithAnnotationCanvas(
                            pageIndex = pageIndex,
                            bitmap = bitmap,
                            editMode = editMode,
                            brushColor = brushColor,
                            brushThickness = brushThickness,
                            annotations = annotations.list.find { it.pageIndex == pageIndex } ?: PageAnnotations(pageIndex),
                            viewModel = viewModel,
                            onStrokeAdded = { stroke -> viewModel.addStroke(pageIndex, stroke) },
                            onHighlightAdded = { x1, y1, x2, y2 -> viewModel.addHighlight(pageIndex, x1, y1, x2, y2, brushColor.replace("#FF", "#60")) },
                            onTapForNote = { offset ->
                                noteDialogCoords = offset
                                notePageIndex = pageIndex
                                noteTextQuery = ""
                                showAddNoteDialog = true
                            },
                            onTapForTextBlock = { offset ->
                                textDialogCoords = offset
                                textPageIndex = pageIndex
                                typedTextQuery = ""
                                typedTextSize = 14f
                                typedTextColorHex = "#FF1F1F1F"
                                typedBold = false
                                typedItalic = false
                                typedWhiteout = false
                                showAddTextDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        Dialog(onDismissRequest = { showAddNoteDialog = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEE70)) // Yellow sticky post style
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Add Sticky Note Comment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = noteTextQuery,
                        onValueChange = { noteTextQuery = it },
                        placeholder = { Text("Write comments...", color = Color.DarkGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = Color.Black,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddNoteDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Black)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (noteTextQuery.isNotBlank()) {
                                    viewModel.addStickyNote(
                                        notePageIndex,
                                        noteTextQuery,
                                        noteDialogCoords.x,
                                        noteDialogCoords.y
                                    )
                                }
                                showAddNoteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.Yellow)
                        ) {
                            Text("Place Note")
                        }
                    }
                }
            }
        }
    }

    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("Add Content Layer", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This text overlays the PDF. Toggle 'Whiteout' to erase original text underneath.", fontSize = 11.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = typedTextQuery,
                        onValueChange = { typedTextQuery = it },
                        placeholder = { Text("Enter editable text...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size: ${typedTextSize.toInt()}sp", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        Slider(
                            value = typedTextSize,
                            onValueChange = { typedTextSize = it },
                            valueRange = 8f..36f,
                            modifier = Modifier.weight(2f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = typedBold,
                                onCheckedChange = { typedBold = it }
                            )
                            Text("Bold", fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = typedItalic,
                                onCheckedChange = { typedItalic = it }
                            )
                            Text("Italic", fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = typedWhiteout,
                                onCheckedChange = { typedWhiteout = it }
                            )
                            Text("Whiteout", fontSize = 11.sp)
                        }
                    }
                    Text("Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#FF1F1F1F", "#FFE53935", "#FF1E88E5", "#FF43A047", "#FF8E24AA").forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                    .border(
                                        width = if (typedTextColorHex == color) 2.dp else 0.dp,
                                        color = Color.Black,
                                        shape = CircleShape
                                    )
                                    .clickable { typedTextColorHex = color }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (typedTextQuery.isNotBlank()) {
                            viewModel.addPdfTextBlock(
                                pageIndex = textPageIndex,
                                text = typedTextQuery,
                                x = textDialogCoords.x,
                                y = textDialogCoords.y,
                                fontSize = typedTextSize,
                                colorHex = typedTextColorHex,
                                isBold = typedBold,
                                isItalic = typedItalic,
                                hasWhiteout = typedWhiteout
                            )
                        }
                        showAddTextDialog = false
                    }
                ) {
                    Text("Insert Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PdfPageWithAnnotationCanvas(
    pageIndex: Int,
    bitmap: Bitmap,
    editMode: String,
    brushColor: String,
    brushThickness: Float,
    annotations: PageAnnotations,
    viewModel: DocViewModel,
    onStrokeAdded: (DrawingStroke) -> Unit,
    onHighlightAdded: (Float, Float, Float, Float) -> Unit,
    onTapForNote: (Offset) -> Unit,
    onTapForTextBlock: (Offset) -> Unit
) {
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }

    // Active stroke coordinates drawing caches
    val activeStrokePoints = remember { mutableStateListOf<Offset>() }
    var activeHighlightStart by remember { mutableStateOf<Offset?>(null) }
    var activeHighlightEnd by remember { mutableStateOf<Offset?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp))
            .testTag("pdf_page_$pageIndex"),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(editMode, canvasSize) {
                    if (canvasSize.width <= 0 || (editMode != "DRAW" && editMode != "HIGHLIGHT")) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (editMode == "DRAW") {
                                activeStrokePoints.clear()
                                activeStrokePoints.add(offset)
                            } else if (editMode == "HIGHLIGHT") {
                                activeHighlightStart = offset
                                activeHighlightEnd = offset
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val position = change.position
                            if (editMode == "DRAW") {
                                activeStrokePoints.add(position)
                            } else if (editMode == "HIGHLIGHT") {
                                activeHighlightEnd = position
                            }
                        },
                        onDragEnd = {
                            if (editMode == "DRAW" && activeStrokePoints.isNotEmpty()) {
                                // Convert stroke coordinates to relative percentages (0f..1f) to respect resizing/devices
                                val rx = activeStrokePoints.map { it.x / canvasSize.width }
                                val ry = activeStrokePoints.map { it.y / canvasSize.height }
                                onStrokeAdded(
                                    DrawingStroke(
                                        pointsX = rx,
                                        pointsY = ry,
                                        colorHex = brushColor,
                                        thickness = brushThickness
                                    )
                                )
                                activeStrokePoints.clear()
                            } else if (editMode == "HIGHLIGHT" && activeHighlightStart != null && activeHighlightEnd != null) {
                                val hs = activeHighlightStart!!
                                val he = activeHighlightEnd!!
                                
                                val x1 = minOf(hs.x, he.x) / canvasSize.width
                                val y1 = minOf(hs.y, he.y) / canvasSize.height
                                val x2 = maxOf(hs.x, he.x) / canvasSize.width
                                val y2 = maxOf(hs.y, he.y) / canvasSize.height

                                onHighlightAdded(x1, y1, x2, y2)
                                activeHighlightStart = null
                                activeHighlightEnd = null
                            }
                        },
                        onDragCancel = {
                            activeStrokePoints.clear()
                            activeHighlightStart = null
                            activeHighlightEnd = null
                        }
                    )
                }
                .pointerInput(editMode, canvasSize) {
                    if (canvasSize.width <= 0 || editMode != "NOTE") return@pointerInput
                    detectTapGestures { offset ->
                        val rx = offset.x / canvasSize.width
                        val ry = offset.y / canvasSize.height
                        onTapForNote(Offset(rx, ry))
                    }
                }
                .pointerInput(editMode, canvasSize) {
                    if (canvasSize.width <= 0 || editMode != "CONTENT") return@pointerInput
                    detectTapGestures { offset ->
                        val rx = offset.x / canvasSize.width
                        val ry = offset.y / canvasSize.height
                        onTapForTextBlock(Offset(rx, ry))
                    }
                }
        ) {
            // 1. PDF Page Base Image
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // 2. Interactive Drawings / Markup Canvas Layer Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width <= 0 || size.height <= 0) return@Canvas

                // 2.1 Draw highlights stored in database model
                for (hl in annotations.highlights) {
                    try {
                        val color = Color(android.graphics.Color.parseColor(hl.colorHex))
                        val left = hl.x1 * size.width
                        val top = hl.y1 * size.height
                        val width = (hl.x2 - hl.x1) * size.width
                        val height = (hl.y2 - hl.y1) * size.height
                        
                        drawRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2.2 Draw active unsaved drag highlight block dynamically
                if (editMode == "HIGHLIGHT" && activeHighlightStart != null && activeHighlightEnd != null) {
                    val hs = activeHighlightStart!!
                    val he = activeHighlightEnd!!
                    val left = minOf(hs.x, he.x)
                    val top = minOf(hs.y, he.y)
                    val w = maxOf(hs.x, he.x) - left
                    val h = maxOf(hs.y, he.y) - top
                    drawRect(
                        color = Color.Yellow.copy(alpha = 0.35f),
                        topLeft = Offset(left, top),
                        size = Size(w, h)
                    )
                }

                // 2.3 Draw strokes stored in database model
                for (stroke in annotations.strokes) {
                    if (stroke.pointsX.size < 2) continue
                    try {
                        val strokeColor = Color(android.graphics.Color.parseColor(stroke.colorHex))
                        val path = Path().apply {
                            moveTo(stroke.pointsX[0] * size.width, stroke.pointsY[0] * size.height)
                            for (p in 1 until stroke.pointsX.size) {
                                lineTo(stroke.pointsX[p] * size.width, stroke.pointsY[p] * size.height)
                            }
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = stroke.thickness,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2.4 Draw active unsaved brush ink stroke dynamically
                if (editMode == "DRAW" && activeStrokePoints.size >= 2) {
                    try {
                        val strokeColor = Color(android.graphics.Color.parseColor(brushColor))
                        val path = Path().apply {
                            moveTo(activeStrokePoints[0].x, activeStrokePoints[0].y)
                            for (p in 1 until activeStrokePoints.size) {
                                lineTo(activeStrokePoints[p].x, activeStrokePoints[p].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = brushThickness,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 3. Render Post-It Sticky Notes inside simple layout positioning
            annotations.notes.forEach { note ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val leftSpace = note.x * canvasSize.width
                val topSpace = note.y * canvasSize.height

                if (canvasSize.width > 0) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { leftSpace.toDp() },
                                y = with(density) { topSpace.toDp() }
                            )
                            .shadow(2.dp, RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFEE70), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                            .widthIn(max = 100.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(2.dp))
                                  Text("Feedback", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Text(note.text, fontSize = 8.sp, color = Color.Black, lineHeight = 9.sp, maxLines = 4)
                        }
                    }
                }
            }

            // 4. Render Editable Text Blocks (Content Editor overlays)
            (annotations.textBlocks ?: emptyList()).forEach { tb ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val leftSpace = tb.x * canvasSize.width
                val topSpace = tb.y * canvasSize.height

                if (canvasSize.width > 0) {
                    var isEditingThisBlock by remember { mutableStateOf(false) }
                    var editedTextState by remember { mutableStateOf(tb.text) }
                    var editedFontSize by remember { mutableStateOf(tb.fontSize) }
                    var editedColorHex by remember { mutableStateOf(tb.colorHex) }
                    var editedBold by remember { mutableStateOf(tb.isBold) }
                    var editedItalic by remember { mutableStateOf(tb.isItalic) }
                    var editedWhiteout by remember { mutableStateOf(tb.hasWhiteout) }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { leftSpace.toDp() },
                                y = with(density) { topSpace.toDp() }
                            )
                            .background(
                                color = if (tb.hasWhiteout) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .then(
                                if (editMode == "CONTENT") {
                                    Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (isEditingThisBlock) MaterialTheme.colorScheme.primary else Color(0x7F1565C0),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .clickable {
                                            isEditingThisBlock = true
                                            editedTextState = tb.text
                                            editedFontSize = tb.fontSize
                                            editedColorHex = tb.colorHex
                                            editedBold = tb.isBold
                                            editedItalic = tb.isItalic
                                            editedWhiteout = tb.hasWhiteout
                                        }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(2.dp)
                    ) {
                        Text(
                            text = tb.text,
                            color = try { Color(android.graphics.Color.parseColor(tb.colorHex)) } catch (e: Exception) { Color.Black },
                            fontSize = (tb.fontSize * (canvasSize.width / 400f * 0.72f)).sp,
                            fontWeight = if (tb.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (tb.isItalic) FontStyle.Italic else FontStyle.Normal,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            modifier = Modifier.widthIn(max = with(density) { (canvasSize.width - leftSpace).toDp() })
                        )
                    }

                    if (isEditingThisBlock && editMode == "CONTENT") {
                        AlertDialog(
                            onDismissRequest = { isEditingThisBlock = false },
                            title = { Text("Edit Content Layer", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = editedTextState,
                                        onValueChange = { editedTextState = it },
                                        label = { Text("Text Content") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Size: ${editedFontSize.toInt()}sp", modifier = Modifier.weight(1f), fontSize = 12.sp)
                                        Slider(
                                            value = editedFontSize,
                                            onValueChange = { editedFontSize = it },
                                            valueRange = 8f..36f,
                                            modifier = Modifier.weight(2f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = editedBold,
                                                onCheckedChange = { editedBold = it }
                                            )
                                            Text("Bold", fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = editedItalic,
                                                onCheckedChange = { editedItalic = it }
                                            )
                                            Text("Italic", fontSize = 11.sp)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = editedWhiteout,
                                                onCheckedChange = { editedWhiteout = it }
                                            )
                                            Text("Whiteout", fontSize = 11.sp)
                                        }
                                    }
                                    Text("Text Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("#FF1F1F1F", "#FFE53935", "#FF1E88E5", "#FF43A047", "#FF8E24AA").forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                                    .border(
                                                        width = if (editedColorHex == color) 2.dp else 0.dp,
                                                        color = Color.Black,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { editedColorHex = color }
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.updatePdfTextBlock(
                                            pageIndex = pageIndex,
                                            blockId = tb.id,
                                            text = editedTextState,
                                            fontSize = editedFontSize,
                                            colorHex = editedColorHex,
                                            isBold = editedBold,
                                            isItalic = editedItalic,
                                            hasWhiteout = editedWhiteout
                                        )
                                        isEditingThisBlock = false
                                    }
                                ) {
                                    Text("Apply")
                                }
                            },
                            dismissButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            viewModel.removePdfTextBlock(pageIndex, tb.id)
                                            isEditingThisBlock = false
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                                    ) {
                                        Text("Delete")
                                    }
                                    TextButton(onClick = { isEditingThisBlock = false }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// 4. PDF SHARING HELPERS
// ==========================================
fun sharePdfBytes(context: Context, documentTitle: String, pdfBytes: ByteArray) {
    try {
        val safeFileName = documentTitle.replace("[^a-zA-Z0-9]".toRegex(), "_") + ".pdf"
        val cacheFile = File(context.cacheDir, safeFileName)
        FileOutputStream(cacheFile).use { it.write(pdfBytes) }

        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, cacheFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PDF Document Export: $documentTitle")
            putExtra(Intent.EXTRA_TEXT, "Here is your exported PDF document.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Export Dynamic PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error compiling or sharing PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
