package com.example.audiodeepfakedetector.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// IMPORTANTE: Aggiunto l'import per recuperare i dati parcheggiati
import com.example.audiodeepfakedetector.data.models.SharedResultData
import java.util.Locale

// Schermata per visualizzare il risultato dell'analisi
@Composable
fun ResultScreen(
    fileName: String,
    fakePercentage: Int,
    duration: Double,
    onBack: () -> Unit
) {
    val bg = Color(0xFF09090D)
    val accentRed = Color(0xFFFF6B81)
    val accentGreen = Color(0xFF4ADE80)
    val accentBlue = Color(0xFF6EA8FE)
    val accentCyan = Color(0xFF52E5FF)

    val isFake = fakePercentage > 50
    val statusColor = if (isFake) accentRed else accentGreen
    val statusText = if (isFake) "DEEPFAKE RILEVATO" else "AUDIO AUTENTICO"
    val confidence = if (isFake) fakePercentage else 100 - fakePercentage

    val spectrum = SharedResultData.currentSpectrum

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            // Barra inferiore con pulsante di ritorno alla home
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg.copy(alpha = 0.95f))
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1F26))
                ) {
                    Text("TORNA ALLA HOME", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-150).dp)
                    .graphicsLayer { alpha = 0.4f }
                    .blur(100.dp)
                    .background(statusColor, CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = fileName,
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Indicatore circolare: feedback visivo per la probabilità predetta dal modello
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                    CircularProgressIndicator(
                        progress = { fakePercentage / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = statusColor,
                        strokeWidth = 12.dp,
                        trackColor = Color(0x1AFFFFFF),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$fakePercentage%",
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "FAKE PROBABILITY",
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(56.dp))

                // Metriche di analisi
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MetricItem("Confidenza", "$confidence%", statusColor)
                    MetricItem("Durata", "${String.format(Locale.US, "%.1f", duration)}s", accentBlue)
                    MetricItem("Affidabilità", if (confidence > 80) "Alta" else "Media", accentCyan)
                }

                Spacer(modifier = Modifier.height(48.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "IMPRONTA DIGITALE (FFT)",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                    ) {
                        RealFrequencySpectrum(spectrum, statusColor)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// Metrica di analisi
@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Text(text = value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// Grafico FFT Analisi
@Composable
fun RealFrequencySpectrum(spectrum: FloatArray, themeColor: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        val width = size.width
        val height = size.height
        val barCount = spectrum.size

        if (barCount > 0) {
            val totalBarWidth = width / barCount
            val spacing = totalBarWidth * 0.15f
            val barWidth = totalBarWidth - spacing

            // Disegna le ampiezze frequenziali
            for (i in 0 until barCount) {
                // Calcola l'intensità dell'ampiezza della frequenza
                val intensity = spectrum[i].coerceIn(0.02f, 1f)
                val barHeight = height * intensity
                val xOffset = i * totalBarWidth + (spacing / 2f)

                // Variazione dinamica opacità basata sull'intensità dell'ampiezza frequenziale
                val dynamicColor = themeColor.copy(alpha = intensity.coerceAtLeast(0.3f))

                drawRect(
                    color = dynamicColor,
                    topLeft = Offset(xOffset, height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
        }
    }
}