package com.example.audiodeepfakedetector

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiodeepfakedetector.ui.screens.*
import com.example.audiodeepfakedetector.ai.InferenceManager
import com.example.audiodeepfakedetector.data.session.SessionManager
import com.example.audiodeepfakedetector.data.session.UserSession
import com.example.audiodeepfakedetector.data.local.database.AppDatabase
import com.example.audiodeepfakedetector.data.local.entities.User
import com.example.audiodeepfakedetector.ui.theme.AudioDeepfakeDetectorTheme
import com.example.audiodeepfakedetector.ui.viewmodels.UploadViewModel
import com.example.audiodeepfakedetector.ui.viewmodels.UploadViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Permette all'app di occupare l'intero schermo
        enableEdgeToEdge()

        setContent {
            AudioDeepfakeDetectorTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                // Inizializzazione Singleton
                val sessionManager = SessionManager(context)
                val db = AppDatabase.getDatabase(context)
                val userDao = db.userDao()

                // Warm-up asincrono del modello AI
                LaunchedEffect(Unit) {
                    InferenceManager.getInstance(context).initialize()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF09090D)
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        val sharedViewModel: UploadViewModel = viewModel(factory = UploadViewModelFactory(context))

                        // Determina la rotta di partenza della navigazione controllando se l'utente ha una sessione salvata
                        val startDest = if (sessionManager.isRemembered()) {
                            UserSession.currentUsername = sessionManager.getSavedUser()
                            "upload"
                        } else {
                            "login"
                        }

                        NavHost(navController = navController, startDestination = startDest) {

                            composable("login") {
                                LoginScreen(
                                    onLoginSubmit = { username, password, rememberMe ->
                                        var isValidUser = false
                                        // Validazione sincrona delle credenziali nel DB
                                        runBlocking(Dispatchers.IO) {
                                            val user = userDao.getUserByUsername(username)
                                            if (user != null && user.password == password) {
                                                isValidUser = true
                                            }
                                        }
                                        // Gestisce l'esito della validazione del login
                                        if (isValidUser) {
                                            UserSession.currentUsername = username
                                            // Gestione persistenza sessione
                                            if (rememberMe) {
                                                sessionManager.saveLogin(username)
                                            } else {
                                                sessionManager.clearSession()
                                            }

                                            navController.navigate("upload") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    onRegisterClick = { navController.navigate("register") }
                                )
                            }

                            composable("register") {
                                RegisterScreen(
                                    onRegisterSuccess = { username, password ->
                                        // Gestisce la registrazione asincrona di un nuovo utente nel DB
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                userDao.registerUser(User(username, password))
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Registrazione completata! Ora accedi.", Toast.LENGTH_SHORT).show()
                                                    navController.popBackStack()
                                                }
                                            } catch (_: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Errore: Username già in uso!", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onBackToLogin = { navController.popBackStack() }
                                )
                            }

                            composable("upload") {
                                // Istanzia la schermata principale
                                UploadScreen(
                                    viewModel = sharedViewModel,
                                    onNavigateToResult = { fileName, fakePercentage, duration ->
                                        navController.navigate("result/$fileName/$fakePercentage/$duration")
                                    },
                                    onHistoryClick = {
                                        sharedViewModel.loadHistory()
                                        navController.navigate("history")
                                    },
                                    onLogout = {
                                        UserSession.logout()
                                        sessionManager.clearSession()
                                        navController.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    onSettingsClick = {
                                        sharedViewModel.loadHistory()
                                        navController.navigate("settings")
                                    }
                                )
                            }

                            // Schermata della cronologia
                            composable("history") {
                                val historyList by sharedViewModel.historyState.collectAsState()
                                HistoryScreen(
                                    historyList = historyList,
                                    onBack = { navController.popBackStack() },
                                    onItemClick = { item ->
                                        navController.navigate("result/${item.fileName}/${item.fakePercentage}/${item.duration}")
                                    }
                                )
                            }

                            // Schermata delle impostazioni
                            composable("settings") {
                                val totalAnalyses by sharedViewModel.analysisCount.collectAsState()

                                SettingsScreen(
                                    username = UserSession.currentUsername ?: "Utente",
                                    totalAnalyses = totalAnalyses,
                                    onConfirmDelete = { enteredPassword ->
                                        sharedViewModel.deleteAccount(
                                            password = enteredPassword,
                                            onSuccess = {
                                                sessionManager.clearSession()
                                                Toast.makeText(context, "Account eliminato con successo", Toast.LENGTH_SHORT).show()
                                                navController.navigate("login") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            },
                                            onError = { errorMessage ->
                                                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // Schermata di result
                            composable(
                                route = "result/{fileName}/{fakePercentage}/{duration}",
                                arguments = listOf(
                                    navArgument("fileName") { type = NavType.StringType },
                                    navArgument("fakePercentage") { type = NavType.IntType },
                                    navArgument("duration") { type = NavType.FloatType }
                                )
                            ) { backStackEntry ->
                                val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                                val fakePercentage = backStackEntry.arguments?.getInt("fakePercentage") ?: 0
                                val duration = backStackEntry.arguments?.getFloat("duration")?.toDouble() ?: 0.0

                                ResultScreen(
                                    fileName = fileName,
                                    fakePercentage = fakePercentage,
                                    duration = duration,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}