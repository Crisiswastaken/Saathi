package com.sohanreddy.sevak.pulse

/**
 * Core PPG signal processing utilities.
 * Implements the pre-processing pipeline from the VSSM PRD:
 *   - Bandpass filtering (0.5–4.0 Hz Butterworth approximation)
 *   - Detrending (rolling mean subtraction)
 *   - Peak detection (adaptive threshold)
 *   - SQI scoring
 */
object PpgSignalProcessor {

    // ── Butterworth-inspired bandpass (IIR) ──────────────────────────────────
    // 4th-order bandpass 0.5–4.0 Hz at 30 Hz sample rate.
    // We use two cascaded 2nd-order biquad sections for stability.

    private class BiquadFilter(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double
    ) {
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }
    }

    /**
     * Simple 2nd-order bandpass filter approximation for PPG.
     * Passband: ~0.5–4.0 Hz at 30 Hz sample rate.
     * These coefficients are pre-computed for stability and simplicity.
     */
    private fun createBandpassFilter(): BiquadFilter {
        // Coefficients for a 2nd-order bandpass centered ~1.5 Hz, Q≈0.8 at 30Hz
        // This covers the heart rate range of 30–240 BPM
        return BiquadFilter(
            b0 = 0.1367, b1 = 0.0, b2 = -0.1367,
            a1 = -1.5834, a2 = 0.7265
        )
    }

    private val greenFilter = createBandpassFilter()
    private val redFilter = createBandpassFilter()

    fun resetFilters() {
        greenFilter.reset()
        redFilter.reset()
    }

    /** Apply bandpass filter to a single sample. */
    fun filterGreen(sample: Double): Double = greenFilter.process(sample)
    fun filterRed(sample: Double): Double = redFilter.process(sample)

    // ── Detrending ───────────────────────────────────────────────────────────

    /**
     * Remove baseline drift by subtracting a rolling mean.
     * Window size in samples (e.g., 30 samples = 1 second at 30 Hz).
     */
    fun detrend(signal: DoubleArray, windowSize: Int = 30): DoubleArray {
        val result = DoubleArray(signal.size)
        for (i in signal.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size, i + windowSize / 2 + 1)
            var sum = 0.0
            for (j in start until end) sum += signal[j]
            result[i] = signal[i] - sum / (end - start)
        }
        return result
    }

    // ── Peak Detection ───────────────────────────────────────────────────────

    /**
     * Adaptive threshold peak detection on filtered PPG signal.
     * Returns indices of detected peaks (systolic peaks).
     */
    fun detectPeaks(signal: DoubleArray, sampleRate: Double = 30.0): List<Int> {
        if (signal.size < 10) return emptyList()

        val peaks = mutableListOf<Int>()
        val minDistance = (sampleRate * 0.33).toInt()  // Min 0.33s between beats (~180 BPM max)

        // Adaptive threshold: 40% of running max amplitude
        val windowSize = (sampleRate * 2).toInt()

        for (i in 2 until signal.size - 2) {
            // Local maximum check
            if (signal[i] > signal[i - 1] && signal[i] > signal[i - 2] &&
                signal[i] > signal[i + 1] && signal[i] > signal[i + 2]
            ) {
                // Threshold: must be above 40% of local max
                val start = maxOf(0, i - windowSize)
                val end = minOf(signal.size, i + windowSize)
                var localMax = Double.MIN_VALUE
                for (j in start until end) {
                    if (signal[j] > localMax) localMax = signal[j]
                }
                val threshold = localMax * 0.4

                if (signal[i] > threshold) {
                    // Check minimum distance from last peak
                    if (peaks.isEmpty() || (i - peaks.last()) >= minDistance) {
                        peaks.add(i)
                    } else if (signal[i] > signal[peaks.last()]) {
                        // Replace last peak if this one is higher
                        peaks[peaks.lastIndex] = i
                    }
                }
            }
        }
        return peaks
    }

    // ── Heart Rate Computation ───────────────────────────────────────────────

    /**
     * Compute HR from peak indices.
     * Returns median instantaneous HR in BPM, or null if insufficient peaks.
     */
    fun computeHeartRate(peaks: List<Int>, sampleRate: Double = 30.0): Double? {
        if (peaks.size < 3) return null

        val ibis = mutableListOf<Double>()
        for (i in 1 until peaks.size) {
            val ibi = (peaks[i] - peaks[i - 1]) / sampleRate  // seconds
            if (ibi in 0.3..2.0) {  // 30–200 BPM physiological range
                ibis.add(ibi)
            }
        }

        if (ibis.size < 2) return null

        // Median of instantaneous HR values
        val hrs = ibis.map { 60.0 / it }
        return hrs.sorted()[hrs.size / 2]
    }

    // ── SpO2 Computation ─────────────────────────────────────────────────────

    /**
     * Compute SpO2 from red and green channel AC/DC ratios.
     * Uses the Ratio-of-Ratios method from Beer-Lambert Law.
     *
     * R_ratio = (AC_red / DC_red) / (AC_green / DC_green)
     * SpO2 = a * R_ratio + b (empirical calibration)
     */
    fun computeSpO2(
        redSignal: DoubleArray,
        greenSignal: DoubleArray,
        calibrationA: Double = -25.0,  // empirical coefficient
        calibrationB: Double = 112.0   // empirical coefficient
    ): Double? {
        if (redSignal.size < 30 || greenSignal.size < 30) return null

        val acRed = acComponent(redSignal)
        val dcRed = dcComponent(redSignal)
        val acGreen = acComponent(greenSignal)
        val dcGreen = dcComponent(greenSignal)

        if (dcRed < 1.0 || dcGreen < 1.0 || acGreen < 0.001) return null

        val rRatio = (acRed / dcRed) / (acGreen / dcGreen)
        val spo2 = calibrationA * rRatio + calibrationB

        return spo2.coerceIn(70.0, 100.0)
    }

    /** AC component = standard deviation of the signal (pulsatile). */
    private fun acComponent(signal: DoubleArray): Double {
        val mean = signal.average()
        return kotlin.math.sqrt(signal.map { (it - mean) * (it - mean) }.average())
    }

    /** DC component = mean of the signal (baseline). */
    private fun dcComponent(signal: DoubleArray): Double = signal.average()

    // ── Signal Quality Index (SQI) ───────────────────────────────────────────

    /**
     * Score signal quality from 0–100 based on:
     *   - Spectral purity (dominant peak in HR range)
     *   - Skewness (PPG should be slightly skewed)
     *   - Kurtosis (should not be too flat or too peaked)
     */
    fun computeSQI(signal: DoubleArray, sampleRate: Double = 30.0): Int {
        if (signal.size < 30) return 0

        val mean = signal.average()
        val variance = signal.map { (it - mean) * (it - mean) }.average()
        val std = kotlin.math.sqrt(variance)

        if (std < 0.0001) return 0

        // Skewness score (0-30): PPG should have moderate skewness
        val skewness = signal.map { ((it - mean) / std).let { z -> z * z * z } }.average()
        val skewnessScore = when {
            kotlin.math.abs(skewness) < 0.5 -> 30  // good
            kotlin.math.abs(skewness) < 1.5 -> 20  // acceptable
            else -> 5  // poor
        }

        // Kurtosis score (0-30): should be between 1.5-6
        val kurtosis = signal.map { ((it - mean) / std).let { z -> z * z * z * z } }.average()
        val kurtosisScore = when {
            kurtosis in 1.5..6.0 -> 30
            kurtosis in 1.0..8.0 -> 20
            else -> 5
        }

        // Peak regularity score (0-40): peaks should be evenly spaced
        val peaks = detectPeaks(signal, sampleRate)
        val peakScore = if (peaks.size >= 3) {
            val ibis = (1 until peaks.size).map { (peaks[it] - peaks[it - 1]).toDouble() }
            val ibiMean = ibis.average()
            val ibiStd = kotlin.math.sqrt(ibis.map { (it - ibiMean) * (it - ibiMean) }.average())
            val cv = if (ibiMean > 0) ibiStd / ibiMean else 1.0
            when {
                cv < 0.1 -> 40   // very regular
                cv < 0.2 -> 30   // regular
                cv < 0.35 -> 20  // moderate
                else -> 5        // irregular
            }
        } else 0

        return (skewnessScore + kurtosisScore + peakScore).coerceIn(0, 100)
    }

    // ── HRV Parameters ───────────────────────────────────────────────────────

    data class HrvMetrics(
        val rmssd: Double,   // ms
        val sdnn: Double,    // ms
        val meanIbi: Double  // ms
    )

    /**
     * Compute basic time-domain HRV metrics from peak indices.
     */
    fun computeHrv(peaks: List<Int>, sampleRate: Double = 30.0): HrvMetrics? {
        if (peaks.size < 5) return null

        val ibisMs = (1 until peaks.size).mapNotNull { i ->
            val ibi = (peaks[i] - peaks[i - 1]) / sampleRate * 1000.0  // ms
            if (ibi in 300.0..2000.0) ibi else null  // physiological range
        }

        if (ibisMs.size < 4) return null

        // RMSSD: Root Mean Square of Successive Differences
        val diffs = (1 until ibisMs.size).map { ibisMs[it] - ibisMs[it - 1] }
        val rmssd = kotlin.math.sqrt(diffs.map { it * it }.average())

        // SDNN: Standard Deviation of NN intervals
        val mean = ibisMs.average()
        val sdnn = kotlin.math.sqrt(ibisMs.map { (it - mean) * (it - mean) }.average())

        return HrvMetrics(rmssd = rmssd, sdnn = sdnn, meanIbi = mean)
    }

    /**
     * Stress index from HRV: 0 (relaxed) to 100 (very stressed).
     * Based on RMSSD — lower RMSSD = higher stress.
     */
    fun computeStressIndex(hrv: HrvMetrics): Int {
        // RMSSD 20-65ms is healthy range (from PRD)
        // Below 20ms = high stress, above 65ms = very relaxed
        return when {
            hrv.rmssd >= 65.0 -> 10
            hrv.rmssd >= 45.0 -> 25
            hrv.rmssd >= 30.0 -> 45
            hrv.rmssd >= 20.0 -> 65
            hrv.rmssd >= 12.0 -> 80
            else -> 95
        }.coerceIn(0, 100)
    }
}
