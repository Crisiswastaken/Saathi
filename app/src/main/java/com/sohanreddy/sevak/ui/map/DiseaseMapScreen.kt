package com.sohanreddy.sevak.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.location.LocationManager
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.*
import com.sohanreddy.sevak.map.DiseaseMapViewModel
import com.sohanreddy.sevak.map.DiseaseReport
import com.sohanreddy.sevak.symptom.DiseaseCategory
import kotlinx.coroutines.launch

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun categoryColor(report: DiseaseReport): Color {
    val hex = DiseaseCategory.fromString(report.category).hexColor
    return Color(android.graphics.Color.parseColor(hex))
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

// 2-3 km radius ≈ zoom level 14.5
private const val HOME_ZOOM = 14.5f

// ── Screen ───────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseMapScreen(
    navController: NavController,
    application: android.app.Application,
    viewModel: DiseaseMapViewModel = viewModel(
        factory = DiseaseMapViewModel.Factory(application)
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val coroutineScope = rememberCoroutineScope()

    // Camera starts zoomed out
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 5f)
    }

    // ── Location permission ──────────────────────────────────────────────────
    var hasLocationPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPerm = granted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPerm) locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ── GPS fetch — runs AFTER permission is confirmed ─────────────────────
    DisposableEffect(hasLocationPerm) {
        var callback: LocationCallback? = null
        val client = LocationServices.getFusedLocationProviderClient(context)

        if (hasLocationPerm) {
            // ── Step 1: Immediately use lastLocation for a fast camera pan ────
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d("DiseaseMapScreen", "lastLocation: ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                        viewModel.setUserLocation(LatLng(loc.latitude, loc.longitude))
                    } else {
                        Log.w("DiseaseMapScreen", "lastLocation is null")
                    }
                }
            } catch (e: Exception) {
                Log.e("DiseaseMapScreen", "lastLocation failed: ${e.message}")
            }

            // ── Step 2: Start active location updates with BALANCED (WiFi/cell) ──
            // HIGH_ACCURACY needs GPS satellites (fails indoors).
            // BALANCED uses WiFi + cell towers — works everywhere.
            Log.d("DiseaseMapScreen", "Starting location updates (BALANCED_POWER_ACCURACY)...")

            val request = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3000L
            )
                .setMinUpdateIntervalMillis(2000L)
                .setMaxUpdates(5)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    Log.d("DiseaseMapScreen", "Fresh fix: ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                    viewModel.setUserLocation(LatLng(loc.latitude, loc.longitude))

                    if (loc.accuracy < 200f) {
                        Log.d("DiseaseMapScreen", "Good accuracy (${loc.accuracy}m), stopping updates")
                        client.removeLocationUpdates(this)
                    }
                }
            }

            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }

        onDispose {
            callback?.let {
                LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(it)
            }
        }
    }

    // ── Animate camera to user location ONCE ─────────────────────────────────
    var hasAnimatedToUser by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.userLocation) {
        val loc = uiState.userLocation
        if (!hasAnimatedToUser && loc != null) {
            Log.d("DiseaseMapScreen", "Animating camera to ${loc.latitude}, ${loc.longitude}")
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, HOME_ZOOM),
                durationMs = 800
            )
            hasAnimatedToUser = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Full-screen map ───────────────────────────────────────────────
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPerm),
            uiSettings = MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true
            )
        ) {
            // ── User location circle + marker ─────────────────────────────
            uiState.userLocation?.let { loc ->
                // Outer glow circle — your vicinity (~150m)
                Circle(
                    center = loc,
                    radius = 150.0,
                    fillColor = Color(0x220091EA),
                    strokeColor = Color(0xFF0091EA),
                    strokeWidth = 2f,
                    clickable = false
                )
                // Inner dot circle — precise location (~25m)
                Circle(
                    center = loc,
                    radius = 25.0,
                    fillColor = Color(0x880091EA),
                    strokeColor = Color(0xFF0091EA),
                    strokeWidth = 2f,
                    clickable = false
                )
                // Pin marker
                Marker(
                    state = MarkerState(position = loc),
                    title = "You are here",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // ── Disease circles ───────────────────────────────────────────
            uiState.reports.forEach { report ->
                val color = categoryColor(report)
                val center = LatLng(report.lat, report.lng)

                Circle(
                    center = center,
                    radius = report.radius.toDouble(),
                    fillColor = color.copy(alpha = 0.35f),
                    strokeColor = color,
                    strokeWidth = 3f,
                    clickable = true,
                    onClick = { _ -> viewModel.onReportSelected(report) }
                )

                // Owner indicator — white dashed ring
                if (report.userId == currentUserId) {
                    Circle(
                        center = center,
                        radius = report.radius.toDouble(),
                        fillColor = Color.Transparent,
                        strokeColor = Color.White,
                        strokeWidth = 2f,
                        strokePattern = listOf(Dash(20f), Gap(10f)),
                        clickable = false
                    )
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // ── Back button — bottom-left ─────────────────────────────────────
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // ── "My Location" recenter button — bottom-right ──────────────────
        IconButton(
            onClick = {
                uiState.userLocation?.let { loc ->
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(loc, HOME_ZOOM),
                            durationMs = 600
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "My Location",
                tint = Color.White
            )
        }
    }

    // ── Bottom sheet for selected report ─────────────────────────────────────
    val selected = uiState.selectedReport
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState()
        val color = categoryColor(selected)

        ModalBottomSheet(
            onDismissRequest = { viewModel.onReportSelected(null) },
            sheetState = sheetState,
            containerColor = Color(0xF0121B36),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = selected.disease,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = color.copy(alpha = 0.2f),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = "${selected.accuracy}% confidence",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (selected.symptoms.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selected.symptoms) { symptom ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = symptom,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

                if (selected.reasoning.isNotBlank()) {
                    Text(
                        text = selected.reasoning,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = relativeTime(selected.timestamp),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Reported anonymously",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}
