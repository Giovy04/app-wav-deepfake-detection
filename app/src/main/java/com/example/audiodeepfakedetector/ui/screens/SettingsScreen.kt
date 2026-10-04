package com.example.audiodeepfakedetector.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

// Gestisce la visualizzazione delle info profilo, statistiche e l'eliminazione account
@Composable
fun SettingsScreen(
    username: String,
    totalAnalyses: Int,
    onConfirmDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val bg = Color(0xFF09090D)
    val card = Color(0xFF11131A)
    val accentRed = Color(0xFFFF6B81)
    val textPrimary = Color(0xFFF5F7FA)
    val textSecondary = Color(0xFFB7C0D1)
    val accentBlue = Color(0xFF6EA8FE)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Scaffold(
        containerColor = bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Impostazioni", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textPrimary)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Sezioni divise
            SettingsSectionTitle("Profilo Utente")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = accentBlue, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = username, color = textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = "Utente", color = accentBlue, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionTitle("Statistiche")
            SettingsCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("Analisi Totali", totalAnalyses.toString(), accentBlue)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionTitle("Informazioni App")
            SettingsCard {
                InfoRow(Icons.Default.Info, "Versione", "1.0.0")
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Elimina account
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentRed.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentRed.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = accentRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Elimina Account", color = accentRed, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Dialog di conferma eliminazione account
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                password = ""
            },
            containerColor = card,
            title = { Text("Elimina Account", color = textPrimary) },
            text = {
                Column {
                    Text("Questa azione è irreversibile. Inserisci la password per confermare.", color = textSecondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = accentRed
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirmDelete(password)
                        showDeleteDialog = false
                        password = ""
                    },
                    enabled = password.isNotBlank()
                ) {
                    Text(
                        text = "CONFERMA",
                        color = if (password.isNotBlank()) accentRed else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        password = ""
                    }
                ) {
                    Text("ANNULLA", color = textSecondary)
                }
            }
        )
    }
}

// Titolo sezione
@Composable
fun SettingsSectionTitle(title: String, color: Color = Color.White.copy(alpha = 0.6f)) {
    Text(
        text = title.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
    )
}

// Card sezione
@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

// Sezione statistica
@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Text(text = value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

// Sezione informazioni app
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}