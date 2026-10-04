package com.example.audiodeepfakedetector.ai

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Analizza un segnale audio grezzo per generare uno spettro di frequenze per l'interfaccia grafica
object FftUtil {
    // Calcola lo spettro di frequenze medio normalizzato di una traccia audio
    fun computeUiSpectrum(samples: FloatArray, size: Int = 1024, bins: Int = 64): FloatArray {
        val result = FloatArray(bins)
        if (samples.isEmpty()) return result

        // Ricerca del picco di ampiezza per la normalizzazione
        var maxAmplitude = 0f
        for (s in samples) {
            if (abs(s) > maxAmplitude) maxAmplitude = abs(s)
        }

        // Normalizzazione del segnale [-1.0, 1.0] per prevenire distorsioni
        val normalizedSamples = if (maxAmplitude > 1e-6f) {
            FloatArray(samples.size) { i -> samples[i] / maxAmplitude }
        } else samples

        // Inizializzazione della Finestra di Hann per ridurre lo spectral leakage
        val hannWindow = FloatArray(size) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat()
        }

        // Individua i segmenti audio con il volume più alto
        val energies = mutableListOf<Pair<Int, Float>>()
        val step = size / 2
        for (i in 0..normalizedSamples.size - size step step) {
            var energy = 0f
            for (j in 0 until size) {
                val v = normalizedSamples[i + j]
                energy += v * v
            }
            energies.add(Pair(i, energy))
        }

        energies.sortByDescending { it.second }
        // Prende solo i 3 segmenti più energetici per ottimizzare i calcoli della UI
        val bestPositions = energies.take(3).map { it.first }

        val accumulatedMagnitudes = FloatArray(size / 2)

        // Somma i risultati spettrali
        for (startPos in bestPositions) {
            val segment = normalizedSamples.sliceArray(startPos until (startPos + size))

            // Rimozione della componente DC (frequenza 0Hz / offset)
            var mean = 0f
            for (v in segment) mean += v
            mean /= segment.size

            // Applicazione della finestra di Hann
            for (j in segment.indices) {
                segment[j] = (segment[j] - mean) * hannWindow[j]
            }

            val magnitudes = computeFftMagnitudes(segment, size)
            magnitudes[0] = 0f // Soppressione esplicita del DC residuale

            val limit = minOf(magnitudes.size, accumulatedMagnitudes.size)
            for (k in 0 until limit) {
                accumulatedMagnitudes[k] += magnitudes[k]
            }
        }

        // Media delle magnitudini calcolate
        for (k in accumulatedMagnitudes.indices) {
            accumulatedMagnitudes[k] /= bestPositions.size.toFloat()
        }

        // Downsampling e conversione in scala logaritmica (Decibel)
        val groupSize = accumulatedMagnitudes.size / bins
        val dbValues = FloatArray(bins)
        var maxDb = Float.NEGATIVE_INFINITY
        var minDb = Float.POSITIVE_INFINITY

        if (groupSize > 0) {
            for (i in 0 until bins) {
                var sum = 0f
                for (j in 0 until groupSize) {
                    val idx = i * groupSize + j
                    if (idx < accumulatedMagnitudes.size) sum += accumulatedMagnitudes[idx]
                }
                val avg = sum / groupSize
                // Conversione in dB per simulare la percezione uditiva umana
                val db = 20f * log10(max(1e-7f, avg))
                dbValues[i] = db
                if (db > maxDb) maxDb = db
                if (db < minDb) minDb = db
            }
        }

        // Fallback per file completamente silenziosi
        if (maxDb == minDb) return FloatArray(bins) { 0.02f }

        // Normalizzazione finale Min-Max [0.02, 1.0] per il rendering grafico
        val range = maxDb - minDb
        for (i in 0 until bins) {
            val normalized = (dbValues[i] - minDb) / range
            result[i] = normalized.coerceIn(0.02f, 1f)
        }

        return result
    }

    // Prepara i buffer e calcola le magnitudini dalla trasformata complessa
    fun computeFftMagnitudes(samples: FloatArray, size: Int): FloatArray {
        // Zero-padding per garantire che la lunghezza N sia potenza di 2 (requisito Cooley-Tukey)
        val n = nextPowerOfTwo(min(samples.size, size))
        val real = FloatArray(n)
        val imag = FloatArray(n)

        // Converte l'audio reale in numeri complessi e applica lo zero-padding
        for (i in 0 until n) {
            if (i < samples.size) {
                real[i] = samples[i]
            } else {
                real[i] = 0f
            }
            imag[i] = 0f
        }

        fft(real, imag)

        // Estrazione modulo dai numeri complessi
        val magnitudes = FloatArray(n / 2)
        for (i in 0 until n / 2) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        return magnitudes
    }

    // Trova la prima potenza di 2 maggiore o uguale a n
    private fun nextPowerOfTwo(n: Int): Int {
        var k = 1
        while (k < n) k *= 2
        return k
    }

    // Algoritmo FFT di Cooley-Tukey
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-Reversal
        val shift = 32 - Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr shift
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
        }

        // Calcolo Butterfly (Radix-2)
        var length = 2
        while (length <= n) {
            val halfLength = length / 2
            val angle = -2.0 * Math.PI / length

            val wLenR = cos(angle).toFloat()
            val wLenI = sin(angle).toFloat()

            var wR = 1f
            var wI = 0f

            // Loop esterno: scorre gli angoli
            for (k in 0 until halfLength) {
                // Loop interno: applica l'angolo a tutti i blocchi paralleli
                for (i in k until n step length) {
                    val j = i + halfLength

                    // Moltiplicazione complessa
                    val vR = real[j] * wR - imag[j] * wI
                    val vI = real[j] * wI + imag[j] * wR

                    val uR = real[i]
                    val uI = imag[i]

                    // Aggiornamento In-Place
                    real[i] = uR + vR
                    imag[i] = uI + vI
                    real[j] = uR - vR
                    imag[j] = uI - vI
                }

                // Avanzamento dell'angolo
                val nextWR = wR * wLenR - wI * wLenI
                wI = wR * wLenI + wI * wLenR
                wR = nextWR
            }
            length = length shl 1
        }
    }
}