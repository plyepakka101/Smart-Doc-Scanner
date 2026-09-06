package com.example.ui.screens

import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ScannedPage
import com.example.ui.OcrStatus
import com.example.ui.ScannerViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrAccessPanel(
    page: ScannedPage,
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ocrStatus by viewModel.ocrStatus.collectAsState()
    
    // Live page info in database
    var livePage by remember { mutableStateOf(page) }
    val pages by viewModel.currentPages.collectAsState()
    
    // Update livePage if database changes
    LaunchedEffect(pages) {
        val updated = pages.find { it.id == page.id }
        if (updated != null) {
            livePage = updated
        }
    }

    // TTS controller states
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var isTtsInitialized by remember { mutableStateOf(false) }

    val rate by viewModel.ttsRate.collectAsState()
    val pitch by viewModel.ttsPitch.collectAsState()

    // Keyword highlighter
    var searchKeyword by remember { mutableStateOf("") }

    // Initialize TTS Engine bound to screen lifecycle
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                // Set default language to Thai or English
                tts?.language = Locale("th", "TH")
            } else {
                Toast.makeText(context, "ระบบอ่านออกเสียงล้มเหลว (TTS Init Failed)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dispose TTS on exit
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // React to speed/pitch updates in real-time if speaking
    LaunchedEffect(rate, pitch) {
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    val pageBitmap = remember(livePage.processedImagePath) {
        val file = File(livePage.processedImagePath)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("สกัดตัวอักษรและเครื่องอ่านออกเสียง (OCR & Accessibility)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal Header previewing image and OCR controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Miniature preview of scanned page
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    if (pageBitmap != null) {
                        Image(
                            bitmap = pageBitmap.asImageBitmap(),
                            contentDescription = "Mini doc",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // OCR Initiation Panel
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("หน้า ${livePage.pageNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (livePage.ocrText.isEmpty() || livePage.ocrText == "Pending OCR Process...") 
                                       "คลาวด์วิเคราะห์: ยังไม่มีการแปลงเอกสาร" 
                                   else "วิเคราะห์สำเร็จ: ทำอักขระเรียบร้อย",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Button triggering actual Gemini OCR
                    var isRunningThisPage = ocrStatus is OcrStatus.Running && (ocrStatus as OcrStatus.Running).pageId == livePage.id
                    Button(
                        onClick = { viewModel.runOcrOnPage(livePage) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isRunningThisPage,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isRunningThisPage) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp).padding(end = 6.dp))
                            Text("กำลังวิเคราะห์อักขระ (OCR)...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.SmartToy, contentDescription = "AI icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("เรียก AI สแกนอักขระด้วย Gemini", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Keyword Highlights Finder Input
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("พิมพ์คำหรือวลี เพื่อค้นหาและไฮไลต์ในหน้านี้...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search text") },
                trailingIcon = {
                    if (searchKeyword.isNotEmpty()) {
                        IconButton(onClick = { searchKeyword = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )

            // MAIN OCR RENDER TEXT DISPLAY BOX
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    val fullOcrText = livePage.ocrText
                    
                    if (fullOcrText.isEmpty() || fullOcrText == "Pending OCR Process...") {
                        // Empty states
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, "AI spark", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "คัดลอก แก้ไข หรือแปลความข้อความทีหลังได้ทีนี่",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else if (ocrStatus is OcrStatus.Running) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Gemini กำลังถอดความภาษาไทยและอังกฤษ...", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator()
                        }
                    } else {
                        // Display actual OCR output with nice Copy/Selection block and highlighted items
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ข้อความที่ได้รับการดึงอักขระ:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                
                                // Direct copy layout
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Scanned OCR data", fullOcrText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "คัดลอกข้อความสำเร็จ!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Copy icon", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("คัดลอกทั้งหมด", fontSize = 11.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val annotatedText = buildAnnotatedString {
                                    if (searchKeyword.isNotEmpty()) {
                                        var startIndex = 0
                                        while (startIndex < fullOcrText.length) {
                                            val index = fullOcrText.indexOf(searchKeyword, startIndex, ignoreCase = true)
                                            if (index == -1) {
                                                append(fullOcrText.substring(startIndex))
                                                break
                                            }
                                            append(fullOcrText.substring(startIndex, index))
                                            withStyle(style = SpanStyle(background = Color.Yellow, color = Color.Black, fontWeight = FontWeight.Bold)) {
                                                append(fullOcrText.substring(index, index + searchKeyword.length))
                                            }
                                            startIndex = index + searchKeyword.length
                                        }
                                    } else {
                                        append(fullOcrText)
                                    }
                                }
                                Text(
                                    text = annotatedText,
                                    fontSize = 14.sp,
                                    style = LocalTextStyle.current.copy(lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // TEXT-TO-SPEECH (TTS) PLAYER CONTROLLER SHELF
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VoiceChat, "Voice speak", tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "ระบบอ่านออกเสียงอัจฉริยะ (Text-To-Speech Reader)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Playing row buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause Action
                        Button(
                            onClick = {
                                if (livePage.ocrText.isEmpty() || livePage.ocrText == "Pending OCR Process...") {
                                    Toast.makeText(context, "ไม่มีข้อความให้อ่าน กรุณาทำ OCR ก่อน!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isTtsPlaying) {
                                    tts?.stop()
                                    isTtsPlaying = false
                                } else {
                                    // Speak text loud
                                    tts?.setSpeechRate(rate)
                                    tts?.setPitch(pitch)

                                    // Queue speech
                                    val speechResult = tts?.speak(
                                        livePage.ocrText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "OcrSpeechEngine"
                                    )
                                    if (speechResult == TextToSpeech.SUCCESS) {
                                        isTtsPlaying = true
                                    } else {
                                        Toast.makeText(context, "เกิดปัญหาในเครื่องยนต์อ่านออกเสียงภาษาไทย", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTtsPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/pause"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTtsPlaying) "หยุดอ่านเสียง" else "เริ่มอ่านข้อความ", fontWeight = FontWeight.Bold)
                        }

                        // Stop Button
                        OutlinedButton(
                            onClick = {
                                tts?.stop()
                                isTtsPlaying = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("หยุดสนิท")
                        }
                    }

                    // Speed Slider config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ปรับระดับความเร็ว (Speed): ${"%.1f".format(rate)}x", fontSize = 11.sp, modifier = Modifier.width(160.dp), fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = rate,
                            onValueChange = { viewModel.ttsRate.value = it },
                            valueRange = 0.5f..2.5f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Pitch Slider config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ปรับระดับโทนเสียง (Pitch): ${"%.1f".format(pitch)}x", fontSize = 11.sp, modifier = Modifier.width(160.dp), fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = pitch,
                            onValueChange = { viewModel.ttsPitch.value = it },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
