package com.sohanreddy.sevak.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WaveformCanvas(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isStatic: Boolean = false,
    amplitude: Float = 0f
) {
    var smoothedAmp by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    var frameTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isStatic) {
        if (isStatic) return@LaunchedEffect
        while (true) {
            withFrameMillis { millis ->
                frameTime = millis
                phase += if (isActive) 0.04f else 0.012f
                val target = if (isActive) amplitude else 0f
                smoothedAmp += (target - smoothedAmp) * 0.08f
            }
        }
    }

    Canvas(modifier = modifier) {
        drawBarWaveform(
            w = size.width,
            h = size.height,
            amplitude = if (isActive) smoothedAmp else 0f,
            phase = phase,
            frameTime = frameTime,
            isActive = isActive,
            isStatic = isStatic
        )
    }
}

private fun DrawScope.drawBarWaveform(
    w: Float,
    h: Float,
    amplitude: Float,
    phase: Float,
    frameTime: Long,
    isActive: Boolean,
    isStatic: Boolean
) {
    val barCount = 57
    val gap = w / (barCount * 1.55f)
    val strokeWidth = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(2.4f)
    val centerY = h / 2f
    val t = frameTime * 0.001f

    repeat(barCount) { index ->
        val norm = index / (barCount - 1).toFloat()
        val x = index * (strokeWidth + gap) + strokeWidth / 2f
        val distanceFromCenter = abs(norm - 0.5f) * 2f
        val cluster = (
            sin(norm * PI.toFloat() * 6f).coerceAtLeast(0f) * 0.72f +
                sin(norm * PI.toFloat() * 18f + 0.7f).coerceAtLeast(0f) * 0.28f
            ) * (1f - distanceFromCenter * 0.18f)
        val idleMotion = if (isStatic) 0f else sin(t * 1.4f + index * 0.34f) * 0.12f
        val liveMotion = sin(phase * 2.2f + index * 0.42f) * 0.18f
        val reactive = if (isActive) amplitude.coerceIn(0f, 1f) * (0.52f + liveMotion) else 0f
        val baseHeight = h * (0.06f + cluster * 0.34f + idleMotion)
        val liveHeight = h * reactive * (0.58f + cluster * 0.5f)
        val barHeight = (baseHeight + liveHeight).coerceIn(h * 0.04f, h * 0.92f)
        val emphasis = (1f - distanceFromCenter).coerceIn(0f, 1f)
        val color = lerp(Color(0x6688AFFF), Color(0xFF8FB1FF), emphasis)

        drawLine(
            color = color.copy(alpha = if (isActive) 0.9f else 0.55f),
            start = Offset(x, centerY - barHeight / 2f),
            end = Offset(x, centerY + barHeight / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
