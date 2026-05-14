package com.sohanreddy.sevak.ui.pulse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import com.sohanreddy.sevak.pulse.MeasurementMode

/**
 * Top-level Pulse composable that manages internal navigation
 * between the landing screen and the measurement screen.
 * Embedded inside the Pulse tab of AuthenticatedAppShell.
 */
@Composable
fun PulseScreen(contentPadding: PaddingValues) {
    var selectedMode by remember { mutableStateOf<MeasurementMode?>(null) }

    if (selectedMode == null) {
        PulseLandingScreen(
            contentPadding = contentPadding,
            onModeSelected = { mode -> selectedMode = mode }
        )
    } else {
        MeasurementScreen(
            mode = selectedMode!!,
            contentPadding = contentPadding,
            onBack = { selectedMode = null }
        )
    }
}
