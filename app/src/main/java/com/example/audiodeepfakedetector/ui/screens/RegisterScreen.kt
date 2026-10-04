package com.example.audiodeepfakedetector.ui.screens

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.PasswordVisualTransformation

// Schermata di registrazione
@Composable
fun RegisterScreen(onRegisterSuccess: (String, String) -> Unit, onBackToLogin: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val bg = Color(0xFF09090D)
    val accentBlue = Color(0xFF6EA8FE)
    val accentPurple = Color(0xFF9B7BFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .graphicsLayer { alpha = 0.5f }
                .blur(80.dp)
                .background(accentBlue, shape = CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Registrati",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Crea un account per salvare le tue analisi",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Campo Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0xFF11131A),
                    unfocusedContainerColor = Color(0xFF11131A)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Campo Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0xFF11131A),
                    unfocusedContainerColor = Color(0xFF11131A)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Conferma Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Conferma Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0xFF11131A),
                    unfocusedContainerColor = Color(0xFF11131A)
                )
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error!!, color = Color(0xFFFF6B81), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    if (username.isEmpty() || password.isEmpty()) {
                        error = "Compila tutti i campi"
                    } else if (password != confirmPassword) {
                        error = "Le password non coincidono"
                    } else {
                        onRegisterSuccess(username, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(accentBlue, accentPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Registrati", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ritorno al Login
            TextButton(onClick = onBackToLogin) {
                Text("Hai già un account? Accedi", color = accentBlue)
            }
        }
    }
}