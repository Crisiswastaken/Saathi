package com.sohanreddy.sevak.pulse

import android.app.Application
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val TAG = "PulseVM"
private const val SESSION_DURATION_SEC = 30
private const val ROI_SIZE = 100  // 100x100 pixel center ROI

// ── Measurement mode ─────────────────────────────────────────────────────────
enum class MeasurementMode { HEART_RATE, SPO2 }

// ── Measurement states ───────────────────────────────────────────────────────
sealed class MeasurementState {
    object Idle : MeasurementState()
    object WaitingForFinger : MeasurementState()
    data class Measuring(val progress: Float, val elapsedSec: Int) : MeasurementState()
    data class Complete(val result: VitalResult) : MeasurementState()
    data class Error(val message: String) : MeasurementState()
}

// ── Results ──────────────────────────────────────────────────────────────────
data class VitalResult(
    val mode: MeasurementMode,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val hrvRmssd: Double? = null,
    val hrvSdnn: Double? = null,
    val stressIndex: Int? = null,
    val signalQuality: Int = 0,
    val durationSec: Int = 0
)

// ── UI State ─────────────────────────────────────────────────────────────────
data class PulseUiState(
    val measurementState: MeasurementState = MeasurementState.Idle,
    val currentMode: MeasurementMode = MeasurementMode.HEART_RATE,
    val liveWaveform: List<Float> = emptyList(),
    val fingerDetected: Boolean = false,
    val instantBpm: Int = 0,   // live BPM estimate during measurement
    val instantSpO2: Int = 0   // live SpO2 estimate during SpO2 measurement
)

// ── ViewModel ────────────────────────────────────────────────────────────────

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    // Camera controls
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraControl: CameraControl? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Signal buffers — all three channels for SpO2 (red + blue) and HR (red)
    private val redValues = mutableListOf<Double>()
    private val greenValues = mutableListOf<Double>()
    private val blueValues = mutableListOf<Double>()
    private val timestamps = mutableListOf<Long>()

    // Waveform display buffer
    private val waveformBuffer = ArrayDeque<Float>(120)

    // Smoothing buffer for finger detection
    private var smoothedRed = 0.0

    // Measurement timing
    private var measurementStartTime = 0L
    private var frameCount = 0
    private var fingerOnCount = 0

    // ── Public API ───────────────────────────────────────────────────────────

    fun startMeasurement(mode: MeasurementMode) {
        resetBuffers()
        _uiState.update {
            it.copy(
                currentMode = mode,
                measurementState = MeasurementState.WaitingForFinger,
                liveWaveform = emptyList(),
                fingerDetected = false,
                instantBpm = 0,
                instantSpO2 = 0
            )
        }
    }

    fun stopMeasurement() {
        releaseCamera()
        _uiState.update {
            it.copy(
                measurementState = MeasurementState.Idle,
                fingerDetected = false,
                liveWaveform = emptyList(),
                instantBpm = 0,
                instantSpO2 = 0
            )
        }
        resetBuffers()
    }

    fun resetToIdle() { stopMeasurement() }

    // ── Camera binding ───────────────────────────────────────────────────────

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewSurfaceProvider: Preview.SurfaceProvider? = null
    ) {
        val context = getApplication<Application>()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                // Image analysis — this is where we get frames
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processFrame(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val preview = previewSurfaceProvider?.let { surfaceProvider ->
                    Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(surfaceProvider) }
                }

                val camera = if (preview != null) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } else {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis
                    )
                }

                // Turn on flash immediately
                cameraControl = camera.cameraControl
                camera.cameraControl.enableTorch(true)
                Log.d(TAG, "Camera bound successfully, torch ON")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                _uiState.update {
                    it.copy(measurementState = MeasurementState.Error("Camera unavailable: ${e.message}"))
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun releaseCamera() {
        try {
            cameraControl?.enableTorch(false)
            cameraProvider?.unbindAll()
            cameraControl = null
        } catch (e: Exception) {
            Log.w(TAG, "Camera release error: ${e.message}")
        }
    }

    // ── Frame Processing (runs on cameraExecutor thread) ─────────────────────

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val (avgRed, avgGreen, avgBlue) = extractRgbFromCenter(imageProxy)
            imageProxy.close()

            // ── Finger detection ─────────────────────────────────────────
            // When finger covers camera + flash:
            //   - Red channel is VERY high (finger tissue absorbs blue/green, reflects red)
            //   - Red > 200, Green/Blue much lower
            //   - Red ratio (R / (R+G+B)) > 0.4
            val total = avgRed + avgGreen + avgBlue
            val redRatio = if (total > 0) avgRed / total else 0.0
            val isFingerOn = avgRed > 50 && redRatio > 0.35 && avgRed > avgGreen

            _uiState.update { it.copy(fingerDetected = isFingerOn) }

            val state = _uiState.value.measurementState

            when {
                state is MeasurementState.WaitingForFinger && isFingerOn -> {
                    fingerOnCount++
                    // Require 5 consecutive frames with finger detected to start
                    if (fingerOnCount >= 5) {
                        Log.d(TAG, "Finger detected! avgR=${"%.1f".format(avgRed)} ratio=${"%.2f".format(redRatio)}")
                        measurementStartTime = System.currentTimeMillis()
                        frameCount = 0
                        _uiState.update {
                            it.copy(measurementState = MeasurementState.Measuring(0f, 0))
                        }
                        recordSample(avgRed, avgGreen, avgBlue)
                    }
                }
                state is MeasurementState.WaitingForFinger && !isFingerOn -> {
                    fingerOnCount = 0  // reset consecutive counter
                }
                state is MeasurementState.Measuring -> {
                    if (!isFingerOn) {
                        fingerOnCount++
                        // Allow up to 10 dropped frames before erroring
                        if (fingerOnCount > 10) {
                            _uiState.update {
                                it.copy(measurementState = MeasurementState.Error(
                                    "Finger lifted. Keep your finger firmly on the camera and flash."
                                ))
                            }
                            resetBuffers()
                        }
                        return
                    }
                    fingerOnCount = 0

                    recordSample(avgRed, avgGreen, avgBlue)
                    frameCount++

                    val elapsed = (System.currentTimeMillis() - measurementStartTime) / 1000.0
                    val progress = (elapsed / SESSION_DURATION_SEC).coerceIn(0.0, 1.0).toFloat()

                    // Live estimations periodically
                    var liveBpm = _uiState.value.instantBpm
                    var liveSpO2 = _uiState.value.instantSpO2
                    if (redValues.size > 150 && redValues.size % 30 == 0) {
                        val hr = computeHeartRateFromRedValues()
                        if (hr != null) liveBpm = hr
                        // Live SpO2 in SpO2 mode
                        if (_uiState.value.currentMode == MeasurementMode.SPO2 && blueValues.size > 150) {
                            val sp = computeSpO2FromChannels()
                            if (sp != null) liveSpO2 = sp
                        }
                    }

                    _uiState.update {
                        it.copy(
                            measurementState = MeasurementState.Measuring(progress, elapsed.toInt()),
                            liveWaveform = waveformBuffer.toList(),
                            instantBpm = liveBpm,
                            instantSpO2 = liveSpO2
                        )
                    }

                    if (elapsed >= SESSION_DURATION_SEC) {
                        finishMeasurement()
                    }
                }
                else -> { /* Idle — ignore */ }
            }
        } catch (e: Exception) {
            imageProxy.close()
            Log.e(TAG, "Frame error: ${e.message}")
        }
    }

    /**
     * Extract average R, G, B from the center ROI of an RGBA_8888 image.
     * This is the proven approach from all reference implementations.
     */
    private fun extractRgbFromCenter(imageProxy: ImageProxy): Triple<Double, Double, Double> {
        val width = imageProxy.width
        val height = imageProxy.height
        val planes = imageProxy.planes

        // RGBA_8888 format: single plane, 4 bytes per pixel
        val buffer = planes[0].buffer
        val rowStride = planes[0].rowStride
        val pixelStride = planes[0].pixelStride

        val roiStartX = (width - ROI_SIZE) / 2
        val roiStartY = (height - ROI_SIZE) / 2
        val roiEndX = minOf(roiStartX + ROI_SIZE, width)
        val roiEndY = minOf(roiStartY + ROI_SIZE, height)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        for (y in roiStartY until roiEndY) {
            for (x in roiStartX until roiEndX) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 2 < buffer.capacity()) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    sumR += r
                    sumG += g
                    sumB += b
                    count++
                }
            }
        }

        return if (count > 0) {
            Triple(sumR.toDouble() / count, sumG.toDouble() / count, sumB.toDouble() / count)
        } else {
            Triple(0.0, 0.0, 0.0)
        }
    }

    private fun recordSample(red: Double, green: Double, blue: Double) {
        redValues.add(red)
        greenValues.add(green)
        blueValues.add(blue)
        timestamps.add(System.currentTimeMillis())

        // Apply simple smoothing for waveform display
        smoothedRed = smoothedRed * 0.8 + red * 0.2
        // Detrend for display: subtract running mean to show AC component
        val windowSize = minOf(60, redValues.size)
        val recentMean = redValues.takeLast(windowSize).average()
        val displayValue = (red - recentMean).toFloat()

        waveformBuffer.addLast(displayValue)
        if (waveformBuffer.size > 100) waveformBuffer.removeFirst()
    }

    // ── Heart Rate Computation ───────────────────────────────────────────────

    private fun computeHeartRateFromRedValues(): Int? {
        if (redValues.size < 90) return null  // Need at least 3 seconds at 30fps

        // Use the last N values
        val values = redValues.toDoubleArray()
        val ts = timestamps.toLongArray()

        // Step 1: Compute actual sample rate from timestamps
        val totalTimeMs = ts.last() - ts.first()
        if (totalTimeMs < 2000) return null
        val sampleRate = (values.size - 1) * 1000.0 / totalTimeMs

        // Step 2: Detrend (subtract rolling average)
        val detrended = PpgSignalProcessor.detrend(values, (sampleRate * 1.5).toInt().coerceAtLeast(10))

        // Step 3: Detect peaks
        val peaks = detectPeaksAdaptive(detrended, sampleRate)
        if (peaks.size < 3) return null

        // Step 4: Compute BPM from inter-beat intervals
        val ibis = mutableListOf<Double>()
        for (i in 1 until peaks.size) {
            // Use actual timestamps for more accurate timing
            val ibiMs = (ts[peaks[i]] - ts[peaks[i - 1]]).toDouble()
            val ibiSec = ibiMs / 1000.0
            if (ibiSec in 0.3..2.0) {  // 30-200 BPM range
                ibis.add(ibiSec)
            }
        }

        if (ibis.size < 2) return null

        // Median BPM
        val bpms = ibis.map { 60.0 / it }
        val sorted = bpms.sorted()
        val median = sorted[sorted.size / 2]

        return median.roundToInt().coerceIn(40, 200)
    }

    /**
     * Adaptive peak detection that works with real camera PPG signals.
     * Uses a sliding window with local maximum detection + minimum distance constraint.
     */
    private fun detectPeaksAdaptive(signal: DoubleArray, sampleRate: Double): List<Int> {
        if (signal.size < 10) return emptyList()

        val peaks = mutableListOf<Int>()
        val minDistSamples = (sampleRate * 0.35).toInt()  // Min 350ms between peaks (~170 BPM)

        // Compute running statistics for adaptive threshold
        val windowSize = (sampleRate * 2.0).toInt().coerceAtLeast(20)

        for (i in 2 until signal.size - 2) {
            // Must be a local maximum (higher than 2 neighbors on each side)
            if (signal[i] <= signal[i - 1] || signal[i] <= signal[i + 1]) continue
            if (signal[i] <= signal[i - 2] || signal[i] <= signal[i + 2]) continue

            // Adaptive threshold: above 30% of local peak-to-trough range
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size, i + windowSize / 2)
            var localMin = Double.MAX_VALUE
            var localMax = Double.MIN_VALUE
            for (j in start until end) {
                if (signal[j] < localMin) localMin = signal[j]
                if (signal[j] > localMax) localMax = signal[j]
            }
            val range = localMax - localMin
            if (range < 0.001) continue  // flat signal, skip

            val threshold = localMin + range * 0.3
            if (signal[i] < threshold) continue

            // Minimum distance from last peak
            if (peaks.isNotEmpty() && (i - peaks.last()) < minDistSamples) {
                // Keep the higher peak
                if (signal[i] > signal[peaks.last()]) {
                    peaks[peaks.lastIndex] = i
                }
                continue
            }

            peaks.add(i)
        }

        return peaks
    }

    // ── Finish Measurement ───────────────────────────────────────────────────

    private fun finishMeasurement() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                releaseCamera()
                val mode = _uiState.value.currentMode

                val hr = computeHeartRateFromRedValues()
                Log.d(TAG, "Final HR: $hr BPM (from ${redValues.size} samples)")

                // SpO2 estimation using RED and BLUE channels (Ratio-of-Ratios)
                val spo2 = if (mode == MeasurementMode.SPO2 && redValues.size > 90 && blueValues.size > 90) {
                    computeSpO2FromChannels()?.coerceIn(85, 100)
                } else null

                // HRV computation
                val totalTimeMs = if (timestamps.size >= 2) timestamps.last() - timestamps.first() else 0L
                val sampleRate = if (totalTimeMs > 0) (timestamps.size - 1) * 1000.0 / totalTimeMs else 30.0
                val detrended = PpgSignalProcessor.detrend(redValues.toDoubleArray(), (sampleRate * 1.5).toInt().coerceAtLeast(10))
                val peaks = detectPeaksAdaptive(detrended, sampleRate)
                val hrv = PpgSignalProcessor.computeHrv(peaks, sampleRate)
                val stress = hrv?.let { PpgSignalProcessor.computeStressIndex(it) }

                // SQI
                val sqi = PpgSignalProcessor.computeSQI(detrended, sampleRate)

                // In HR mode, HR is required. In SpO2 mode, SpO2 is required.
                if (mode == MeasurementMode.HEART_RATE && hr == null) {
                    _uiState.update {
                        it.copy(measurementState = MeasurementState.Error(
                            "Could not detect heartbeat. Please ensure your finger fully covers the camera and flash, and hold still."
                        ))
                    }
                    return@launch
                }
                if (mode == MeasurementMode.SPO2 && spo2 == null) {
                    _uiState.update {
                        it.copy(measurementState = MeasurementState.Error(
                            "Could not estimate SpO₂. Ensure your finger fully covers both the camera lens and the flash. Try pressing slightly harder."
                        ))
                    }
                    return@launch
                }

                val result = VitalResult(
                    mode = mode,
                    heartRate = hr,
                    spo2 = spo2,
                    hrvRmssd = hrv?.rmssd,
                    hrvSdnn = hrv?.sdnn,
                    stressIndex = stress,
                    signalQuality = sqi,
                    durationSec = SESSION_DURATION_SEC
                )

                Log.d(TAG, "Result: HR=${result.heartRate}, SpO2=${result.spo2}, SQI=$sqi, Stress=$stress")

                _uiState.update {
                    it.copy(measurementState = MeasurementState.Complete(result))
                }
            } catch (e: Exception) {
                Log.e(TAG, "finishMeasurement error: ${e.message}", e)
                _uiState.update {
                    it.copy(measurementState = MeasurementState.Error("Measurement failed: ${e.message}"))
                }
            }
        }
    }

    /**
     * SpO2 using Windowed Ratio-of-Ratios method.
     *
     * Uses sliding 5-second windows to compute per-window RoR values,
     * then takes the median for robustness. This avoids the problem of
     * whole-session StdDev being dominated by drift instead of pulsatile AC.
     *
     * Formula per window:
     *   AC_red = StdDev(red_window), DC_red = Mean(red_window)
     *   AC_blue = StdDev(blue_window), DC_blue = Mean(blue_window)
     *   R_window = (AC_red / DC_red) / (AC_blue / DC_blue)
     *
     * Final SpO2 = 110 - 25 * median(R)
     *
     * Calibration curve: Camera-based SpO2 studies (Kanva et al., 2014)
     * use SpO2 = A - B*R where A≈110, B≈25 for smartphone cameras.
     * This produces values in the 94-99% range for healthy individuals,
     * matching the expected physiological range.
     */
    private fun computeSpO2FromChannels(): Int? {
        if (redValues.size < 90 || blueValues.size < 90) return null

        // Compute actual sample rate
        val totalTimeMs = if (timestamps.size >= 2) timestamps.last() - timestamps.first() else return null
        if (totalTimeMs < 3000) return null
        val sampleRate = (timestamps.size - 1) * 1000.0 / totalTimeMs

        // Use 5-second windows with 50% overlap for windowed analysis
        val windowSamples = (sampleRate * 5.0).toInt().coerceAtLeast(30)
        val stepSamples = windowSamples / 2
        val redArr = redValues.toDoubleArray()
        val blueArr = blueValues.toDoubleArray()

        val windowRatios = mutableListOf<Double>()

        var start = 0
        while (start + windowSamples <= redArr.size) {
            val redWindow = redArr.sliceArray(start until start + windowSamples)
            val blueWindow = blueArr.sliceArray(start until start + windowSamples)

            val meanRed = redWindow.average()
            val meanBlue = blueWindow.average()

            // Skip windows with very low signal (finger not fully covering)
            if (meanRed < 5.0 || meanBlue < 0.5) {
                start += stepSamples
                continue
            }

            val stdRed = kotlin.math.sqrt(redWindow.map { (it - meanRed) * (it - meanRed) }.average())
            val stdBlue = kotlin.math.sqrt(blueWindow.map { (it - meanBlue) * (it - meanBlue) }.average())

            // Need both channels to have measurable pulsatile component
            if (stdRed < 0.01 || stdBlue < 0.001) {
                start += stepSamples
                continue
            }

            val ratioRed = stdRed / meanRed
            val ratioBlue = stdBlue / meanBlue
            val R = ratioRed / ratioBlue

            // Sanity check: R should be between 0.3 and 3.0 for valid readings
            if (R in 0.3..3.0) {
                windowRatios.add(R)
            }

            start += stepSamples
        }

        if (windowRatios.size < 2) {
            Log.w(TAG, "SpO2: Only ${windowRatios.size} valid windows, not enough data")
            return null
        }

        // Take median R for robustness
        val sorted = windowRatios.sorted()
        val medianR = sorted[sorted.size / 2]

        // Calibration: SpO2 = 110 - 25 * R
        // This curve is calibrated for smartphone cameras where:
        //   R ≈ 0.4-0.5 → SpO2 ≈ 97-99% (normal)
        //   R ≈ 0.6-0.8 → SpO2 ≈ 90-95% (low)
        //   R > 1.0 → SpO2 < 85% (critical)
        val spo2 = 110.0 - 25.0 * medianR

        Log.d(TAG, "SpO2: windows=${windowRatios.size} ratios=${windowRatios.map { "%.3f".format(it) }}" +
                " medianR=${ "%.3f".format(medianR)} SpO2=${ "%.1f".format(spo2)}")

        return spo2.roundToInt().coerceIn(85, 99)
    }

    private fun resetBuffers() {
        redValues.clear()
        greenValues.clear()
        blueValues.clear()
        timestamps.clear()
        waveformBuffer.clear()
        frameCount = 0
        fingerOnCount = 0
        smoothedRed = 0.0
        PpgSignalProcessor.resetFilters()
    }

    override fun onCleared() {
        releaseCamera()
        cameraExecutor.shutdown()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PulseViewModel(application) as T
    }
}
