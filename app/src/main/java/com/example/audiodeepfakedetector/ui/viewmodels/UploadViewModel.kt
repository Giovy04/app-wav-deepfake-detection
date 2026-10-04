package com.example.audiodeepfakedetector.ui.viewmodels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiodeepfakedetector.ai.AudioProcessor
import com.example.audiodeepfakedetector.ai.InferenceManager
import com.example.audiodeepfakedetector.data.session.UserSession
import com.example.audiodeepfakedetector.data.local.entities.Detection
import com.example.audiodeepfakedetector.data.models.DetectionHistory
import com.example.audiodeepfakedetector.ai.FftUtil
import com.example.audiodeepfakedetector.data.local.database.AppDatabase
import com.example.audiodeepfakedetector.data.models.SharedResultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

data class UploadUiState(
    val isOnnxReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisStageMessage: String? = null,
    val errorMessage: String? = null,
    val selectedFileUri: Uri? = null,
    val fileName: String = "",
    val duration: Double = 0.0,
    val spectrum: FloatArray = FloatArray(0)
)

class UploadViewModel(
    private val inferenceManager: InferenceManager,
    private val context: Context
) : ViewModel() {

    private val audioProcessor = AudioProcessor(context)
    private val _uiState = MutableStateFlow(UploadUiState(isOnnxReady = inferenceManager.isModelLoaded))
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()
    private val _historyState = MutableStateFlow<List<DetectionHistory>>(emptyList())
    val historyState: StateFlow<List<DetectionHistory>> = _historyState.asStateFlow()
    private val _analysisCount = MutableStateFlow(0)
    val analysisCount: StateFlow<Int> = _analysisCount.asStateFlow()

    // Carica il modello AI in background
    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (!inferenceManager.isModelLoaded) {
                inferenceManager.initialize()
            }
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.update { it.copy(isOnnxReady = inferenceManager.isModelLoaded) }
            }
        }
    }

    // Recupera la cronologia delle analisi dell'utente dal database in background
    fun loadHistory() {
        val username = UserSession.currentUsername ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val detections = db.detectionDao().getDetectionsForUser(username)
                val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

                val historyList = detections.map {
                    DetectionHistory(
                        id = it.id,
                        fileName = it.fileName,
                        fakePercentage = it.fakePercentage,
                        date = dateFormatter.format(Date(it.timestamp)),
                        duration = it.duration
                    )
                }
                _historyState.value = historyList
                _analysisCount.value = historyList.size
            } catch (e: Exception) {
                Log.e("UploadViewModel", "Failed to load history", e)
            }
        }
    }

    // Eliminazione account
    fun deleteAccount(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val username = UserSession.currentUsername ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val user = db.userDao().getUserByUsername(username)

                // Verifica che l'utente esista e la password sia corretta
                if (user != null && user.password == password) {
                    db.detectionDao().deleteDetectionsForUser(username)
                    db.userDao().deleteUser(username)
                    UserSession.logout()

                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("Password errata. Riprova.")
                    }
                }
            } catch (e: Exception) {
                Log.e("UploadViewModel", "Errore nell'eliminazione account", e)
                withContext(Dispatchers.Main) {
                    onError("Si è verificato un errore imprevisto.")
                }
            }
        }
    }

    // Controlla che il file audio selezionato dall'utente rispetti i limiti
    private fun validateAudioLimits(uri: Uri): Boolean {
        val maxSizeMb = 50
        val maxDurationMs = 5 * 60 * 1000L

        // Controllo del peso
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    val sizeBytes = it.getLong(sizeIndex)
                    val sizeMB = sizeBytes / (1024 * 1024)
                    if (sizeMB > maxSizeMb) {
                        _uiState.update { state ->
                            state.copy(
                                errorMessage = "E007: Il file supera il limite massimo di ${maxSizeMb}MB.",
                                selectedFileUri = null,
                                fileName = "",
                                duration = 0.0
                            )
                        }
                        return false
                    }
                }
            }
        }

        // Controllo della durata
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            if (durationMs > maxDurationMs) {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = "E008: L'audio supera il limite massimo di 5 minuti.",
                        selectedFileUri = null,
                        fileName = "",
                        duration = 0.0
                    )
                }
                false
            } else {
                true
            }
        } catch (_: Exception) {
            _uiState.update { state ->
                state.copy(
                    errorMessage = "E009: Impossibile leggere i metadati o formato corrotto.",
                    selectedFileUri = null,
                    fileName = "",
                    duration = 0.0
                )
            }
            false
        } finally {
            retriever.release()
        }
    }

    // Valida il formato, verifica i limiti e pre-elabora la traccia audio in background
    fun onFileSelected(uri: Uri, fileName: String) {
        // Valida formato
        if (!fileName.lowercase(Locale.getDefault()).endsWith(".wav")) {
            _uiState.update {
                it.copy(
                    errorMessage = "E001: Formato non supportato. Seleziona esclusivamente file .wav",
                    selectedFileUri = null,
                    fileName = "",
                    duration = 0.0
                )
            }
            return
        }

        // Valida limiti
        if (!validateAudioLimits(uri)) return

        // Decodifica audio in background
        viewModelScope.launch(Dispatchers.IO) {
            val samples = audioProcessor.loadWav(uri)
            val durationSeconds = if (samples.isNotEmpty()) samples.size / AudioProcessor.TARGET_SAMPLE_RATE.toDouble() else 0.0

            _uiState.update {
                it.copy(
                    selectedFileUri = uri,
                    fileName = fileName,
                    duration = durationSeconds,
                    errorMessage = if (samples.isEmpty()) "E002: Il file .wav non può essere letto o è corrotto." else null
                )
            }
        }
    }

    // Gestisce e propaga gli errori all'interfaccia utente
    fun onError(message: String) {
        _uiState.update { it.copy(errorMessage = message, selectedFileUri = null) }
    }

    // Esegue l'intera pipeline di rilevamento deepfake audio
    fun onAnalyzeClick(onNavigateToResult: (String, Int, Double) -> Unit) {
        val currentState = _uiState.value
        if (currentState.isAnalyzing) return
        if (!inferenceManager.isModelLoaded) {
            _uiState.update { it.copy(errorMessage = "E006: Il modello AI non è pronto.") }
            return
        }

        currentState.selectedFileUri?.let { uri ->
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    isAnalyzing = true,
                    analysisStageMessage = "Lettura e decodifica WAV in corso..."
                )
            }

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val samples = audioProcessor.loadWav(uri)

                    if (samples.isEmpty()) {
                        _uiState.update { it.copy(errorMessage = "E002: Impossibile leggere il file o formato corrotto.", isAnalyzing = false) }
                        return@launch
                    }

                    val durationSeconds = samples.size / AudioProcessor.TARGET_SAMPLE_RATE.toDouble()
                    if (durationSeconds < 1.0) {
                        _uiState.update { it.copy(errorMessage = "E003: Audio troppo corto per l'analisi (min 1s).", isAnalyzing = false) }
                        return@launch
                    }

                    _uiState.update { it.copy(analysisStageMessage = "Preparazione blocchi audio per l'AI...") }
                    val chunks = audioProcessor.prepareForWavLM(samples)

                    if (chunks.isEmpty()) {
                        _uiState.update { it.copy(errorMessage = "E003: Audio troppo silenzioso o vuoto.", isAnalyzing = false) }
                        return@launch
                    }

                    _uiState.update { it.copy(analysisStageMessage = "Esecuzione AI su ${chunks.size} blocchi...") }
                    val probabilities = mutableListOf<Float>()

                    // Esegue l'inferenza dell'IA su ogni singolo chunk
                    for (chunk in chunks) {
                        val inferenceResult = inferenceManager.runInference(chunk)
                        if (inferenceResult.size >= 2 && inferenceResult[1] >= 0f) {
                            probabilities.add(inferenceResult[1])
                        }
                    }

                    val finalProb = if (probabilities.isNotEmpty()) probabilities.average().toFloat() else 0.0f
                    val fakePercentage = (finalProb * 100).toInt().coerceIn(0, 100)

                    Log.d("UploadViewModel", "Probabilità chunk: $probabilities -> Media Finale: $fakePercentage%")

                    _uiState.update { it.copy(analysisStageMessage = "Generazione Grafico Spettrale...") }
                    val previewSpectrum = FftUtil.computeUiSpectrum(samples, size = 1024, bins = 64)

                    // Salva il risultato dell'analisi nel database
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val username = UserSession.currentUsername ?: "anonymous"
                        db.detectionDao().insertDetection(
                            Detection(
                                username = username,
                                fileName = currentState.fileName,
                                fakePercentage = fakePercentage,
                                timestamp = System.currentTimeMillis(),
                                duration = durationSeconds
                            )
                        )
                        loadHistory()
                    } catch (e: Exception) {
                        Log.e("UploadViewModel", "Failed to save history to Room DB", e)
                    }

                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            analysisStageMessage = null,
                            spectrum = previewSpectrum
                        )
                    }

                    SharedResultData.currentSpectrum = previewSpectrum
                    delay(500.milliseconds)

                    viewModelScope.launch(Dispatchers.Main) {
                        onNavigateToResult(currentState.fileName, fakePercentage, durationSeconds)
                    }

                } catch (e: Exception) {
                    Log.e("UploadViewModel", "Errore critico durante la pipeline", e)
                    _uiState.update {
                        it.copy(
                            errorMessage = "E006: Errore durante l'analisi: ${e.localizedMessage}",
                            isAnalyzing = false,
                            analysisStageMessage = null
                        )
                    }
                }
            }
        } ?: _uiState.update { it.copy(errorMessage = "E002: Seleziona un file prima di analizzare.") }
    }
}