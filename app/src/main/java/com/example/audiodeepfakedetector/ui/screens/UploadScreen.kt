package com.example.audiodeepfakedetector.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiodeepfakedetector.ui.viewmodels.UploadViewModel
import com.example.audiodeepfakedetector.ui.viewmodels.UploadViewModelFactory
import java.util.Locale

@Composable
fun UploadScreen(
    onNavigateToResult: (String, Int, Double) -> Unit,
    onHistoryClick: () -> Unit,
    onLogout: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: UploadViewModel = viewModel(factory = UploadViewModelFactory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            viewModel.onError("Permesso negato. Non è possibile accedere ai file audio.")
        }
    }

    // Apre picker di sistema
    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else "Sconosciuto"
                } ?: "Sconosciuto"
            } catch (_: Exception) {
                "Sconosciuto"
            }

            // Validazione MIME, verifica formato .wav
            val mimeType = context.contentResolver.getType(it)
            val isWav = fileName.lowercase().endsWith(".wav") ||
                    mimeType == "audio/wav" ||
                    mimeType == "audio/x-wav" ||
                    mimeType == "audio/vnd.wave"

            if (isWav) {
                viewModel.onFileSelected(it, fileName)
            } else {
                viewModel.onError("E001: Formato non supportato. Selezionare un file WAV valido.")
            }
        }
    }

    // Controlla i permessi per leggere i file audio
    fun checkAndLaunchPicker() {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            selectFileLauncher.launch("audio/*")
        } else {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    val bg = Color(0xFF09090D)
    val card = Color(0xFF11131A)
    val card2 = Color(0xFF161A22)
    val stroke = Color(0x26FFFFFF)
    val textPrimary = Color(0xFFF5F7FA)
    val textSecondary = Color(0xFFB7C0D1)
    val accentBlue = Color(0xFF6EA8FE)
    val accentPurple = Color(0xFF9B7BFF)
    val accentCyan = Color(0xFF52E5FF)
    val accentRed = Color(0xFFFF6B81)
    val accentGreen = Color(0xFF4ADE80)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = 0.65f }
                .blur(70.dp)
                .background(Color(0xFF5B8CFF), shape = CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Detector",
                    color = textSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
                Row {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History", tint = accentBlue)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = textSecondary)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = accentRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Audio Deepfake Detector",
                color = textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = accentPurple.copy(alpha = 0.20f),
                        spotColor = accentBlue.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, stroke)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Selezione file audio",
                        color = textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isOnnxReady) accentGreen else accentRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isOnnxReady) "ONNX Engine: Ready" else "ONNX Engine: Loading/Error",
                            color = if (state.isOnnxReady) accentGreen else accentRed,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Scegli un file WAV per verificare se la traccia audio è reale o generata artificialmente.",
                        color = textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Limitazioni: durata audio (1s - 5m), dimensione massima 50Mb",
                        color = accentBlue.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (state.isAnalyzing) {
                        ScanningAnimation(
                            message = state.analysisStageMessage ?: "ANALISI IN CORSO..."
                        )
                    } else {
                        Button(
                            onClick = { checkAndLaunchPicker() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.horizontalGradient(listOf(accentBlue, accentPurple, accentCyan))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Seleziona file WAV", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (state.selectedFileUri != null && !state.isAnalyzing) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = card2)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accentCyan))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.fileName,
                                        color = textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (state.duration > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Durata: ${String.format(Locale.US, "%.1f", state.duration)}s",
                                        color = textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.onAnalyzeClick(onNavigateToResult) },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentCyan)
                        ) {
                            Text("Avvia Analisi", color = bg, fontWeight = FontWeight.Bold)
                        }
                    }

                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = message, color = accentRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningAnimation(message: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xFF0D1117), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scanLineY = size.height * offsetY

                val barCount = 20
                val barWidth = size.width / barCount
                for (i in 0 until barCount) {
                    val barHeight = size.height * (0.3f + 0.4f * ((i * 7) % 10 / 10f))
                    drawRect(
                        color = Color(0xFF6EA8FE).copy(alpha = 0.2f),
                        topLeft = Offset(i * barWidth + 2f, size.height - barHeight),
                        size = Size(barWidth - 4f, barHeight)
                    )
                }

                drawLine(
                    color = Color(0xFF52E5FF),
                    start = Offset(0f, scanLineY),
                    end = Offset(size.width, scanLineY),
                    strokeWidth = 2.dp.toPx()
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF52E5FF).copy(alpha = 0.3f), Color.Transparent),
                        startY = scanLineY - 10.dp.toPx(),
                        endY = scanLineY + 10.dp.toPx()
                    ),
                    topLeft = Offset(0f, scanLineY - 10.dp.toPx()),
                    size = Size(size.width, 20.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message.uppercase(),
            color = Color(0xFF52E5FF),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}