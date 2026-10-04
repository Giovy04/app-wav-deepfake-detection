package com.example.audiodeepfakedetector.ai

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlin.math.min

// Rappresenta un singolo blocco audio (chunk) pronto per essere processato dal modello AI
data class AudioChunk(
    val inputValues: FloatArray, // Array contenente i campioni audio
    val attentionMask: LongArray, // Maschera per il modello transformer (1 = dato reale, 0 = padding)
    val validLength: Int // Numero di campioni effettivi prima dell'inizio del padding
)

// Gestisce l'estrazione, la decodifica hardware e il pre-processing del segnale audio
class AudioProcessor(private val context: Context) {
    // Parametri di configurazione vincolati dall'architettura del modello AI
    companion object {
        const val TARGET_SAMPLE_RATE = 16000f
        const val CHUNK_DURATION_SECONDS = 2
        const val SAMPLES_PER_CHUNK = (TARGET_SAMPLE_RATE * CHUNK_DURATION_SECONDS).toInt()
        const val OVERLAP_SECONDS = 1
        const val SAMPLES_OVERLAP = (TARGET_SAMPLE_RATE * OVERLAP_SECONDS).toInt()
        const val MAX_DURATION_SECONDS = 300
    }

    // Legge un file audio, lo decodifica e restituisce un array di campioni normalizzati e ricampionati
    fun loadWav(uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        // Buffer iniziale pre-allocato
        var floatBuffer = FloatArray(1024 * 1024)
        var totalSamples = 0
        var sampleRate: Float
        var channels: Int
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            // Identificazione della traccia audio all'interno del contenitore multimediale
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) return floatArrayOf()

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return floatArrayOf()

            // Estrazione metadati dinamici
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).toFloat()
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }

            val maxSamplesAllowed = (sampleRate * MAX_DURATION_SECONDS).toInt()

            // Inizializzazione Decoder Hardware
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isEOS = false

            // Decodifica Audio Streaming
            while (true) {
                // Fase di input: Spinge i byte compressi nel decoder
                if (!isEOS) {
                    // Chiede buffer vuoto
                    val inIndex = codec.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        // Legge byte compressi
                        val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1

                        if (sampleSize < 0) {
                            // Fine del file raggiunta, inviamo il flag EOS al codec
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Fase di output: Estraiamo i dati PCM raw decodificati
                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)

                        // Gestione dinamica della profondità di bit (16-bit vs 32-bit float)
                        val chunkFloat = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val floatBuf = buffer.asFloatBuffer()
                            FloatArray(floatBuf.remaining()).apply { floatBuf.get(this) }
                        } else {
                            // Normalizzazione standard [-1.0, 1.0] per il formato a 16 bit
                            val shortBuf = buffer.asShortBuffer()
                            FloatArray(shortBuf.remaining()) { shortBuf.get(it).toFloat() / 32768.0f }
                        }

                        // Downmix a canale singolo per la pipeline AI
                        val monoChunk = toMono(chunkFloat, channels)

                        // Raddoppio dinamico del buffer
                        if (totalSamples + monoChunk.size > floatBuffer.size) {
                            floatBuffer = floatBuffer.copyOf(floatBuffer.size * 2)
                        }

                        // Copia in memoria nel buffer principale
                        val samplesToCopy = min(monoChunk.size, maxSamplesAllowed - totalSamples)
                        if (samplesToCopy > 0) {
                            System.arraycopy(monoChunk, 0, floatBuffer, totalSamples, samplesToCopy)
                            totalSamples += samplesToCopy
                        }

                        // Protezione OOM: Interruzione immediata se superiamo il limite di tempo
                        if (totalSamples >= maxSamplesAllowed) {
                            Log.w("AudioProcessor", "Limite di $MAX_DURATION_SECONDS secondi raggiunto. Troncamento per evitare OOM.")
                            break
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Gestione per i cambiamenti dinamici del codec durante lo streaming
                    val newFormat = codec.outputFormat
                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).toFloat()
                    channels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (newFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = newFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }

                // Condizione di uscita dal loop
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || totalSamples >= maxSamplesAllowed) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("AudioProcessor", "Errore durante la decodifica audio", e)
            return floatArrayOf()
        } finally {
            // Memory Leak Prevention: pulizia e rilascio delle risorse
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.e("AudioProcessor", "Errore durante il rilascio del codec", e)
            }
            extractor.release()
        }

        // Taglio dell'eccesso pre-allocato e passaggio alla fase di DSP
        val finalData = floatBuffer.copyOfRange(0, totalSamples)
        return resample(finalData, sampleRate)
    }

    // Esegue il ricampionamento spaziale tramite interpolazione cubica
    private fun resample(data: FloatArray, sourceSr: Float): FloatArray {
        if (sourceSr == TARGET_SAMPLE_RATE || sourceSr == 0f || data.isEmpty()) return data

        val ratio = sourceSr / TARGET_SAMPLE_RATE
        val outputSize = (data.size / ratio).toInt()
        val output = FloatArray(outputSize)

        for (i in 0 until outputSize) {
            val sourceIndex = i * ratio
            val index = sourceIndex.toInt()
            val mu = sourceIndex - index

            // Recupero dei 4 punti adiacenti con clamping ai bordi per evitare IndexOutOfBounds
            val y0 = if (index - 1 >= 0) data[index - 1] else data[index]
            val y1 = data[index]
            val y2 = if (index + 1 < data.size) data[index + 1] else data[index]
            val y3 = if (index + 2 < data.size) data[index + 2] else y2

            // Calcolo dei coefficienti polinomiali
            val a0 = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3
            val a1 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
            val a2 = -0.5f * y0 + 0.5f * y2

            // Risoluzione dell'equazione cubica per interpolare il nuovo campione
            output[i] = a0 * mu * mu * mu + a1 * mu * mu + a2 * mu + y1
        }
        return output
    }

    // Downmix da audio multicanale a canale singolo calcolando la media aritmetica dei campioni
    private fun toMono(data: FloatArray, numChannels: Int): FloatArray {
        if (numChannels <= 1 || data.isEmpty()) return data
        val monoData = FloatArray(data.size / numChannels)
        for (i in monoData.indices) {
            var sum = 0f
            for (c in 0 until numChannels) {
                sum += data[i * numChannels + c]
            }
            monoData[i] = sum / numChannels.toFloat()
        }
        return monoData
    }

    // Segmenta il segnale continuo in una lista di tensori a dimensione fissa
    fun prepareForWavLM(data: FloatArray): List<AudioChunk> {
        if (data.isEmpty()) return emptyList()
        val chunks = mutableListOf<AudioChunk>()
        var start = 0

        // Crea il blocco aggiungendo zero-padding se necessario
        fun buildChunkWithPadding(sourceData: FloatArray, startIndex: Int, lengthToCopy: Int): AudioChunk {
            val paddedInput = FloatArray(SAMPLES_PER_CHUNK) // Inizializza automaticamente a 0.0f (silenzio)
            val attentionMask = LongArray(SAMPLES_PER_CHUNK) // Inizializza automaticamente a 0L (ignora)

            // Copia dei dati reali
            System.arraycopy(sourceData, startIndex, paddedInput, 0, lengthToCopy)

            // Valorizza la maschera di attenzione solo per i campioni reali
            for (i in 0 until lengthToCopy) {
                attentionMask[i] = 1L
            }

            return AudioChunk(paddedInput, attentionMask, lengthToCopy)
        }

        // Caso limite: l'audio totale è più corto del requisito del modello
        if (data.size < SAMPLES_PER_CHUNK) {
            chunks.add(buildChunkWithPadding(data, 0, data.size))
            return chunks
        }

        // Finestra scorrevole con sovrapposizione
        while (start + SAMPLES_PER_CHUNK <= data.size) {
            chunks.add(buildChunkWithPadding(data, start, SAMPLES_PER_CHUNK))
            start += (SAMPLES_PER_CHUNK - SAMPLES_OVERLAP)
        }

        // Gestione della coda rimanente del segnale
        val leftover = data.size - start
        if (leftover > 0) {
            chunks.add(buildChunkWithPadding(data, start, leftover))
        }

        return chunks
    }
}