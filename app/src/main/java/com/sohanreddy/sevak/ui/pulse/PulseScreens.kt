package com.sohanreddy.sevak.ui.pulse

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohanreddy.sevak.R
import com.sohanreddy.sevak.pulse.MeasurementMode
import com.sohanreddy.sevak.pulse.MeasurementState
import com.sohanreddy.sevak.pulse.PulseViewModel
import com.sohanreddy.sevak.pulse.VitalResult

private val HeartRed = Color(0xFFF65987)
private val HeartRedStrong = Color(0xFFFF4A67)
private val SpO2Blue = Color(0xFF2E86FF)
private val AppInk = Color(0xFF10233F)
private val AppMuted = Color(0xFF566983)
private val AppSoftBlue = Color(0xFFF7FAFF)
private val AppBlush = Color(0xFFFDF7FB)

@Composable
fun PulseLandingScreen(
    contentPadding: PaddingValues,
    onModeSelected: (MeasurementMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFDFEFF),
                        AppSoftBlue,
                        AppBlush
                    )
                )
            )
            .padding(contentPadding)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Pulse",
                color = AppInk,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Camera-based vital signs monitoring",
                color = AppMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(2.dp))

        PulseImageCard(
            imageRes = R.drawable.heart_rate_section_box,
            contentDescription = "Heart Rate",
            aspectRatio = 660f / 417f,
            onClick = { onModeSelected(MeasurementMode.HEART_RATE) }
        )

        PulseImageCard(
            imageRes = R.drawable.spo2_section_box,
            contentDescription = "SpO2",
            aspectRatio = 1428f / 916f,
            onClick = { onModeSelected(MeasurementMode.SPO2) }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "How it works",
                    color = AppInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Place your fingertip over the rear camera and flash. Keep still while Saathi reads subtle color changes from the camera feed.",
                    color = AppMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
                Text(
                    text = "Wellness estimate only. For clinical concerns, consult a healthcare professional.",
                    color = Color(0xFFEC6476),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PulseImageCard(
    imageRes: Int,
    contentDescription: String,
    aspectRatio: Float,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.74f)),
        tonalElevation = 2.dp,
        shadowElevation = 14.dp
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        )
    }
}

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val primaryColor = if (mode == MeasurementMode.HEART_RATE) HeartRedStrong else SpO2Blue
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraIsBound by remember { mutableStateOf(false) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPerm = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPerm, previewView) {
        if (hasCameraPerm && !cameraIsBound) {
            viewModel.startMeasurement(mode)
            viewModel.bindCamera(lifecycleOwner, previewView.surfaceProvider)
            cameraIsBound = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseCamera()
            viewModel.resetToIdle()
        }
    }

    val retryMeasurement = {
        viewModel.releaseCamera()
        cameraIsBound = false
        viewModel.startMeasurement(mode)
        viewModel.bindCamera(lifecycleOwner, previewView.surfaceProvider)
        cameraIsBound = true
    }

    MeasurementBackgroundFrame(
        mode = mode,
        contentPadding = contentPadding
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val state = uiState.measurementState
            val metricTop = maxHeight * if (mode == MeasurementMode.HEART_RATE) 0.37f else 0.34f

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularCameraPreview(previewView = previewView)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = statusTitle(state, uiState.fingerDetected, hasCameraPerm),
                    color = AppInk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = statusSubtitle(state, mode, hasCameraPerm),
                    color = AppMuted.copy(alpha = 0.52f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                MeasurementProgress(state = state, primaryColor = primaryColor)
            }

            IconButton(
                onClick = {
                    viewModel.stopMeasurement()
                    onBack()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 44.dp, end = 22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF344159),
                    modifier = Modifier.size(34.dp)
                )
            }

            MetricOverlay(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = metricTop),
                mode = mode,
                state = state,
                instantBpm = uiState.instantBpm,
                instantSpO2 = uiState.instantSpO2
            )

            when (state) {
                is MeasurementState.Idle -> {
                    if (!hasCameraPerm) {
                        PermissionSheet(
                            primaryColor = primaryColor,
                            onGrantPermission = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        )
                    }
                }
                is MeasurementState.Complete -> {
                    ResultSheet(
                        result = state.result,
                        primaryColor = primaryColor,
                        onRetry = retryMeasurement,
                        onDone = onBack,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
                is MeasurementState.Error -> {
                    ErrorSheet(
                        message = state.message,
                        primaryColor = primaryColor,
                        onRetry = retryMeasurement,
                        onBack = onBack,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MeasurementBackgroundFrame(
    mode: MeasurementMode,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(
                if (mode == MeasurementMode.HEART_RATE) {
                    R.drawable.heart_rate_bg
                } else {
                    R.drawable.spo2_bg
                }
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.96f),
                            Color.White.copy(alpha = 0.72f),
                            Color.White.copy(alpha = 0.0f)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
private fun CircularCameraPreview(previewView: PreviewView) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .shadow(12.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color(0xFFEAF1FA))
            .border(3.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                (previewView.parent as? ViewGroup)?.removeView(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MeasurementProgress(
    state: MeasurementState,
    primaryColor: Color
) {
    if (state is MeasurementState.Measuring) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .width(142.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp)),
            color = primaryColor,
            trackColor = primaryColor.copy(alpha = 0.16f)
        )
    }
}

@Composable
private fun MetricOverlay(
    mode: MeasurementMode,
    state: MeasurementState,
    instantBpm: Int,
    instantSpO2: Int,
    modifier: Modifier = Modifier
) {
    val value = when (state) {
        is MeasurementState.Complete -> {
            if (mode == MeasurementMode.HEART_RATE) {
                state.result.heartRate
            } else {
                state.result.spo2
            }
        }
        is MeasurementState.Measuring -> {
            if (mode == MeasurementMode.HEART_RATE) {
                instantBpm.takeIf { it > 0 }
            } else {
                instantSpO2.takeIf { it > 0 }
            }
        }
        else -> null
    }
    val unit = if (mode == MeasurementMode.HEART_RATE) "bpm" else "%"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value?.toString() ?: "--",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 50.sp
        )
        Text(
            text = unit,
            color = Color.White,
            fontSize = if (mode == MeasurementMode.HEART_RATE) 28.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
    }
}

private fun statusTitle(
    state: MeasurementState,
    fingerDetected: Boolean,
    hasCameraPermission: Boolean
): String {
    if (!hasCameraPermission) return "Camera access needed"
    return when (state) {
        is MeasurementState.WaitingForFinger -> {
            if (fingerDetected) "Finger detected" else "No finger detected"
        }
        is MeasurementState.Measuring -> "Measuring"
        is MeasurementState.Complete -> "Reading complete"
        is MeasurementState.Error -> "Measurement failed"
        is MeasurementState.Idle -> "Preparing camera"
    }
}

private fun statusSubtitle(
    state: MeasurementState,
    mode: MeasurementMode,
    hasCameraPermission: Boolean
): String {
    if (!hasCameraPermission) return "Allow camera access to continue"
    return when (state) {
        is MeasurementState.WaitingForFinger -> "Press your finger on camera"
        is MeasurementState.Measuring -> {
            val label = if (mode == MeasurementMode.HEART_RATE) "Heart rate" else "SpO2"
            "$label reading ${state.elapsedSec}s / 30s"
        }
        is MeasurementState.Complete -> "You can retake or return to Pulse"
        is MeasurementState.Error -> "Adjust your finger and try again"
        is MeasurementState.Idle -> "Setting up live preview"
    }
}

@Composable
private fun PermissionSheet(
    primaryColor: Color,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSheet(modifier = modifier) {
        Text(
            text = "Camera permission required",
            color = AppInk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Saathi needs camera access to read your fingertip and show the live placement preview.",
            color = AppMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Button(
            onClick = onGrantPermission,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
        ) {
            Text("Grant permission")
        }
    }
}

@Composable
private fun ResultSheet(
    result: VitalResult,
    primaryColor: Color,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSheet(modifier = modifier) {
        Text(
            text = "Result details",
            color = AppInk,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (result.mode == MeasurementMode.SPO2 && result.heartRate != null) {
                MetricRow("Heart Rate", "${result.heartRate} bpm", primaryColor)
            }
            if (result.spo2 != null) {
                val status = when {
                    result.spo2 >= 95 -> "Normal"
                    result.spo2 >= 92 -> "Mild concern"
                    else -> "Alert"
                }
                MetricRow("SpO2 Status", status, primaryColor)
            }
            if (result.hrvRmssd != null) {
                MetricRow("HRV", "${"%.1f".format(result.hrvRmssd)} ms", AppMuted)
            }
            if (result.stressIndex != null) {
                val stressLabel = when {
                    result.stressIndex <= 25 -> "Relaxed"
                    result.stressIndex <= 50 -> "Normal"
                    result.stressIndex <= 70 -> "Moderate"
                    else -> "High"
                }
                MetricRow("Stress", stressLabel, AppMuted)
            }
            MetricRow("Signal Quality", "${result.signalQuality}/100", AppMuted)
        }
        Text(
            text = "Wellness estimate only. Not for clinical diagnosis.",
            color = Color(0xFFEC6476),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
            ) {
                Text("Retake")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ErrorSheet(
    message: String,
    primaryColor: Color,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSheet(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Try once more",
                color = AppInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = message,
            color = AppMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppMuted)
            ) {
                Text("Back")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun GlassSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.82f)),
        tonalElevation = 2.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
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
            color = AppMuted
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
