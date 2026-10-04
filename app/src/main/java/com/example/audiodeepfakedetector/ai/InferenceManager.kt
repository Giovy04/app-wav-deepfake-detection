package com.example.audiodeepfakedetector.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp

// Gestore dell'inferenza AI basato su ONNX Runtime
class InferenceManager private constructor(context: Context) {
    private val appContext: Context = context.applicationContext

    // Ambiente nativo ONNX
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    val isModelLoaded: Boolean get() = session != null

    companion object {
        @Volatile
        private var INSTANCE: InferenceManager? = null

        // Serve a garantire che esista una sola istanza di InferenceManager
        fun getInstance(context: Context): InferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Inizializza il modello ONNX caricandolo in memoria in modo asincrono
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (session != null) return@withContext

        try {
            Log.d("InferenceManager", "Inizializzazione modello ONNX quantizzato...")

            val modelName = "model_quantized_am.onnx"
            val modelFile = File(appContext.cacheDir, modelName)

            // Copia del modello in cache se non presente
            if (!modelFile.exists()) {
                Log.d("InferenceManager", "Copia del modello in cache...")
                appContext.assets.open(modelName).use { inputStream ->
                    modelFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // Limitazione Thread per migliori performance
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2) // Parallelizzazione interna al singolo nodo
                setInterOpNumThreads(1) // Parallelizzazione tra nodi diversi
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            session = env.createSession(modelFile.absolutePath, sessionOptions)

            Log.i(
                "InferenceManager",
                "Modello ONNX caricato! Nodi di input richiesti: ${session!!.inputNames}"
            )
        } catch (e: Exception) {
            Log.e("InferenceManager", "Errore critico durante il caricamento del modello", e)
        }
    }

    // Esegue l'inferenza di classificazione audio passando un frammento audio al modello ONNX
    fun runInference(chunk: AudioChunk): FloatArray {
        val currentSession = session ?: return floatArrayOf(0.5f, 0.5f)

        try {
            val shape = longArrayOf(1, chunk.inputValues.size.toLong())

            // Incapsula entrambi gli array nei rispettivi buffer nativi
            val floatBuffer = FloatBuffer.wrap(chunk.inputValues)
            val maskBuffer = LongBuffer.wrap(chunk.attentionMask)

            // Alloca il primo tensore (valori audio)
            return OnnxTensor.createTensor(env, floatBuffer, shape).use { inputValuesTensor ->
                // Alloca il secondo tensore (attention mask)
                OnnxTensor.createTensor(env, maskBuffer, shape).use { attentionMaskTensor ->

                    // Passa la mappa completa richiesta da WavLM
                    val inputs = mapOf(
                        "input_values" to inputValuesTensor,
                        "attention_mask" to attentionMaskTensor
                    )

                    // Esecuzione ONNX Runtime
                    currentSession.run(inputs).use { results ->

                        if (!results.iterator().hasNext()) return floatArrayOf(0.5f, 0.5f)

                        val resultValue = results.get("logits").orElseGet { results.get(0) }

                        val rawOutput = when (val outputValue = resultValue.value) {
                            is Array<*> -> {
                                when (val first = outputValue.firstOrNull()) {
                                    is FloatArray -> first
                                    is Array<*> -> (first.firstOrNull() as? FloatArray)
                                        ?: floatArrayOf()

                                    else -> floatArrayOf()
                                }
                            }

                            is FloatArray -> outputValue
                            else -> floatArrayOf()
                        }

                        if (rawOutput.size < 2) {
                            Log.e(
                                "InferenceManager",
                                "Errore Parsing: dimensione logits errata = ${rawOutput.size}"
                            )
                            return floatArrayOf(0.5f, 0.5f)
                        }

                        Log.d(
                            "InferenceManager",
                            "RAW LOGITS: Fake(0)=${rawOutput[0]}, Real(1)=${rawOutput[1]}"
                        )

                        val maxVal = rawOutput.maxOrNull() ?: 0f
                        val exps = rawOutput.map { exp((it - maxVal).toDouble()) }
                        val sumExps = exps.sum()

                        val pFake = (exps[0] / sumExps).toFloat()
                        val pReal = (exps[1] / sumExps).toFloat()

                        // Restituisce le due probabilità di classificazione
                        floatArrayOf(pReal.coerceIn(0f, 1f), pFake.coerceIn(0f, 1f))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InferenceManager", "Errore durante l'inferenza (Crash)", e)
            return floatArrayOf(0.5f, 0.5f)
        }
    }
}