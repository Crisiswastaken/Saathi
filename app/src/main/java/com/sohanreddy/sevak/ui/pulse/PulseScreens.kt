package com.sohanreddy.sevak.ui.pulse

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohanreddy.sevak.pulse.MeasurementMode
import com.sohanreddy.sevak.pulse.MeasurementState
import com.sohanreddy.sevak.pulse.PulseViewModel
import com.sohanreddy.sevak.pulse.VitalResult

// ── Color palette ────────────────────────────────────────────────────────────
private val HeartRed = Color(0xFFE53935)
private val HeartRedDark = Color(0xFF8B0000)
private val SpO2Blue = Color(0xFF2196F3)
private val SpO2BlueDark = Color(0xFF0D47A1)
private val SurfaceDark = Color(0xFF0F1629)
private val CardDark = Color(0xFF1A2340)
private val CardBorder = Color(0xFF2A3456)

// ── Pulse Landing Screen ─────────────────────────────────────────────────────

@Composable
fun PulseLandingScreen(
    contentPadding: PaddingValues,
    onModeSelected: (MeasurementMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SurfaceDark, Color(0xFF070B18))
                )
            )
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Pulse",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Camera-based vital signs monitoring",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(48.dp))

            // Heart Rate Card
            VitalCard(
                title = "Heart Rate",
                subtitle = "Measure your pulse using the camera",
                icon = Icons.Filled.Favorite,
                unit = "BPM",
                primaryColor = HeartRed,
                gradientColors = listOf(HeartRedDark, HeartRed),
                onClick = { onModeSelected(MeasurementMode.HEART_RATE) }
            )

            Spacer(Modifier.height(20.dp))

            // SpO2 Card
            VitalCard(
                title = "SpO₂",
                subtitle = "Estimate blood oxygen saturation",
                icon = Icons.Filled.Air,
                unit = "%",
                primaryColor = SpO2Blue,
                gradientColors = listOf(SpO2BlueDark, SpO2Blue),
                onClick = { onModeSelected(MeasurementMode.SPO2) }
            )

            Spacer(Modifier.height(32.dp))

            // Info card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardDark.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "How it works",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Place your fingertip on the rear camera lens. The flash will illuminate your finger to detect blood volume changes (PPG). Keep still for 30 seconds.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "⚠️ This is not a medical device. Consult a doctor for clinical diagnosis.",
                        fontSize = 12.sp,
                        color = Color(0xFFFBC02D).copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VitalCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    unit: String,
    primaryColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(gradientColors),
                    RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Start",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    mode: MeasurementMode,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: PulseViewModel = viewModel(
        factory = PulseViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val primaryColor = if (mode == MeasurementMode.HEART_RATE) HeartRed else SpO2Blue
    val modeName = if (mode == MeasurementMode.HEART_RATE) "Heart Rate" else "SpO₂"

    // Camera permission
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPerm = granted }

    // Track if we've already bound the camera for this session
    var cameraIsBound by remember { mutableStateOf(false) }

    // Request camera permission on first launch
    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Once we have permission, start measurement and bind camera ONCE
    LaunchedEffect(hasCameraPerm) {
        if (hasCameraPerm && !cameraIsBound) {
            viewModel.startMeasurement(mode)
            viewModel.bindCamera(lifecycleOwner)
            cameraIsBound = true
        }
    }

    // Clean up camera on exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseCamera()
            viewModel.resetToIdle()
        }
    }

    // Helper to retry: release + rebind
    val retryMeasurement = {
        viewModel.releaseCamera()
        cameraIsBound = false
        viewModel.startMeasurement(mode)
        viewModel.bindCamera(lifecycleOwner)
        cameraIsBound = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SurfaceDark, Color(0xFF070B18))
                )
            )
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.stopMeasurement()
                    onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = modeName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(32.dp))

            // Content based on state
            when (val state = uiState.measurementState) {
                is MeasurementState.WaitingForFinger -> {
                    WaitingForFingerUI(primaryColor)
                }
                is MeasurementState.Measuring -> {
                    MeasuringUI(
                        state = state,
                        primaryColor = primaryColor,
                        waveform = uiState.liveWaveform,
                        fingerDetected = uiState.fingerDetected,
                        instantBpm = uiState.instantBpm,
                        instantSpO2 = uiState.instantSpO2,
                        mode = mode
                    )
                }
                is MeasurementState.Complete -> {
                    ResultUI(
                        result = state.result,
                        primaryColor = primaryColor,
                        onRetry = retryMeasurement,
                        onDone = onBack
                    )
                }
                is MeasurementState.Error -> {
                    ErrorUI(
                        message = state.message,
                        primaryColor = primaryColor,
                        onRetry = retryMeasurement,
                        onBack = onBack
                    )
                }
                is MeasurementState.Idle -> {
                    if (!hasCameraPerm) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(Modifier.height(80.dp))
                            Text(
                                text = "Camera permission required",
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Waiting for Finger ───────────────────────────────────────────────────────

@Composable
private fun WaitingForFingerUI(primaryColor: Color) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))

        // Pulsing finger icon
        Box(
            modifier = Modifier
                .size((120 * scale).dp)
                .background(primaryColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((80 * scale).dp)
                    .background(primaryColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Place finger",
                    tint = primaryColor,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Place your finger",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cover the rear camera and flash with\nyour fingertip. Apply gentle pressure.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// ── Measuring ────────────────────────────────────────────────────────────────

@Composable
private fun MeasuringUI(
    state: MeasurementState.Measuring,
    primaryColor: Color,
    waveform: List<Float>,
    fingerDetected: Boolean,
    instantBpm: Int = 0,
    instantSpO2: Int = 0,
    mode: MeasurementMode = MeasurementMode.HEART_RATE
) {
    // Determine which live value to show based on mode
    val isSpO2Mode = mode == MeasurementMode.SPO2
    val hasLiveValue = if (isSpO2Mode) instantSpO2 > 0 else instantBpm > 0
    val liveValueText = if (isSpO2Mode) "$instantSpO2" else "$instantBpm"
    val liveUnitText = if (isSpO2Mode) "%" else "BPM"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress ring
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(160.dp),
                color = primaryColor,
                strokeWidth = 6.dp,
                trackColor = primaryColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasLiveValue) {
                    Text(
                        text = liveValueText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = liveUnitText,
                        fontSize = 13.sp,
                        color = primaryColor.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = "${state.elapsedSec}s",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "/ 30s",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (isSpO2Mode) "Measuring SpO\u2082... ${state.elapsedSec}s / 30s"
                   else "Measuring... ${state.elapsedSec}s / 30s",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Keep your finger still on the camera",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.4f)
        )

        Spacer(Modifier.height(32.dp))

        // Live waveform
        if (waveform.size > 10) {
            Text(
                text = "PPG Waveform",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))

            WaveformCanvas(
                waveform = waveform,
                color = primaryColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(CardDark.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun WaveformCanvas(
    waveform: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (waveform.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val midY = height / 2

        // Normalize waveform to fit canvas
        val maxAbs = waveform.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(0.001f) ?: 1f
        val amplitude = height * 0.4f

        val path = Path()
        val stepX = width / (waveform.size - 1).coerceAtLeast(1)

        waveform.forEachIndexed { i, value ->
            val x = i * stepX
            val y = midY - (value / maxAbs) * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }
}

// ── Result ────────────────────────────────────────────────────────────────────

@Composable
private fun ResultUI(
    result: VitalResult,
    primaryColor: Color,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Main metric
        if (result.mode == MeasurementMode.HEART_RATE && result.heartRate != null) {
            MetricDisplay(
                value = "${result.heartRate}",
                unit = "BPM",
                label = "Heart Rate",
                color = primaryColor
            )
        } else if (result.mode == MeasurementMode.SPO2 && result.spo2 != null) {
            MetricDisplay(
                value = "${result.spo2}",
                unit = "%",
                label = "Blood Oxygen",
                color = primaryColor
            )
        } else {
            Text(
                text = "Could not compute result",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(28.dp))

        // Secondary metrics grid
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardDark.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Heart rate (always shown if available)
                if (result.heartRate != null && result.mode == MeasurementMode.SPO2) {
                    MetricRow("Heart Rate", "${result.heartRate} BPM", primaryColor)
                    Spacer(Modifier.height(12.dp))
                }

                // SpO2 status
                if (result.spo2 != null) {
                    val spo2Status = when {
                        result.spo2 >= 95 -> "Normal" to Color(0xFF4CAF50)
                        result.spo2 >= 92 -> "Mild Concern" to Color(0xFFFFC107)
                        else -> "Alert" to Color(0xFFE53935)
                    }
                    MetricRow("SpO₂ Status", spo2Status.first, spo2Status.second)
                    Spacer(Modifier.height(12.dp))
                }

                // HRV
                if (result.hrvRmssd != null) {
                    MetricRow("HRV (RMSSD)", "${"%.1f".format(result.hrvRmssd)} ms", Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                }

                // Stress
                if (result.stressIndex != null) {
                    val stressLabel = when {
                        result.stressIndex <= 25 -> "Relaxed" to Color(0xFF4CAF50)
                        result.stressIndex <= 50 -> "Normal" to Color(0xFF8BC34A)
                        result.stressIndex <= 70 -> "Moderate" to Color(0xFFFFC107)
                        else -> "High" to Color(0xFFE53935)
                    }
                    MetricRow("Stress Level", stressLabel.first, stressLabel.second)
                    Spacer(Modifier.height(12.dp))
                }

                // Signal quality
                val sqiColor = when {
                    result.signalQuality >= 70 -> Color(0xFF4CAF50)
                    result.signalQuality >= 50 -> Color(0xFFFFC107)
                    else -> Color(0xFFE53935)
                }
                MetricRow("Signal Quality", "${result.signalQuality}/100", sqiColor)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Disclaimer
        Text(
            text = "⚠️ Not a medical device. For wellness purposes only.\nConsult a healthcare professional for clinical diagnosis.",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = primaryColor
                )
            ) {
                Text("Retake", fontSize = 15.sp)
            }

            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor
                )
            ) {
                Text("Done", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun MetricDisplay(
    value: String,
    unit: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = unit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorUI(
    message: String,
    primaryColor: Color,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))

        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Error",
            tint = primaryColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Measurement Failed",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Go Back")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Try Again")
            }
        }
    }
}
