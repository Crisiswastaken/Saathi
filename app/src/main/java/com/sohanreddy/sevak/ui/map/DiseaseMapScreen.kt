package com.sohanreddy.sevak.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.sohanreddy.sevak.map.DiseaseMapViewModel
import com.sohanreddy.sevak.map.DiseaseReport
import com.sohanreddy.sevak.map.DiseaseZone
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// 2-3 km radius ≈ zoom level 14.5
private const val HOME_ZOOM = 14.5f
private const val ZONE_RADIUS_METERS = 1000.0  // 1 km radius for zones
private val ZoneYellow = Color(0xFFFBC02D)
private val ZoneRed = Color(0xFFE53935)

// ── Zone color logic ─────────────────────────────────────────────────────────
// 1–10 reports = yellow, 11+ = red
private fun zoneColor(count: Int): Color = if (count > 10) ZoneRed else ZoneYellow

// ── Disease label bitmap for map markers ─────────────────────────────────────
private fun diseaseLabelIcon(
    disease: String,
    accuracy: Int,
    count: Int,
    color: Color
): BitmapDescriptor {
    val title = disease.ifBlank { "Disease" }.take(28)
    val detail = "$accuracy% • $count report${if (count == 1) "" else "s"}"
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textSize = 34f
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = 26f
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
    }
    val titleBounds = Rect()
    val detailBounds = Rect()
    titlePaint.getTextBounds(title, 0, title.length, titleBounds)
    detailPaint.getTextBounds(detail, 0, detail.length, detailBounds)

    val hPad = 28
    val vPad = 20
    val gap = 10
    val width = maxOf(titleBounds.width(), detailBounds.width()) + hPad * 2
    val height = titleBounds.height() + detailBounds.height() + vPad * 2 + gap
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(232, 18, 27, 54)
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
    canvas.drawRoundRect(RectF(2f, 2f, width - 2f, height - 2f), 16f, 16f, strokePaint)

    val titleX = (width - titleBounds.width()) / 2f
    val titleY = vPad - titleBounds.top.toFloat()
    val detailX = (width - detailBounds.width()) / 2f
    val detailY = titleY + titleBounds.height() + gap - detailBounds.top
    canvas.drawText(title, titleX, titleY, titlePaint)
    canvas.drawText(detail, detailX, detailY, detailPaint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
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

// ── Screen ───────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseMapScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: DiseaseMapViewModel = viewModel(
        factory = DiseaseMapViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

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

    // ── GPS fetch — HIGH_ACCURACY for precise location ───────────────────────
    DisposableEffect(hasLocationPerm) {
        var callback: LocationCallback? = null
        val client = LocationServices.getFusedLocationProviderClient(context)

        if (hasLocationPerm) {
            // Step 1: Immediately use lastLocation for a fast camera pan
            try {
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d("DiseaseMapScreen", "lastLocation: ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                        viewModel.setUserLocation(LatLng(loc.latitude, loc.longitude))
                    }
                }
            } catch (e: Exception) {
                Log.e("DiseaseMapScreen", "lastLocation failed: ${e.message}")
            }

            // Step 2: Active high-accuracy updates for precise GPS fix
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000L
            )
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdates(10)
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    Log.d("DiseaseMapScreen", "Fresh fix: ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                    viewModel.setUserLocation(LatLng(loc.latitude, loc.longitude))

                    if (loc.accuracy < 50f) {
                        Log.d("DiseaseMapScreen", "Precise fix (${loc.accuracy}m), stopping updates")
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
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, HOME_ZOOM),
                durationMs = 800
            )
            hasAnimatedToUser = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
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
            // ── User location circle (yellow, 1km) ───────────────────────
            uiState.userLocation?.let { loc ->
                Circle(
                    center = loc,
                    radius = ZONE_RADIUS_METERS,
                    fillColor = ZoneYellow.copy(alpha = 0.12f),
                    strokeColor = ZoneYellow.copy(alpha = 0.6f),
                    strokeWidth = 2f,
                    clickable = false
                )
                // Inner precise dot (~30m)
                Circle(
                    center = loc,
                    radius = 30.0,
                    fillColor = ZoneYellow.copy(alpha = 0.55f),
                    strokeColor = ZoneYellow,
                    strokeWidth = 2f,
                    clickable = false
                )
            }

            // ── Disease zone circles with labels ─────────────────────────
            uiState.zones.forEach { zone ->
                val color = zoneColor(zone.count)

                Circle(
                    center = zone.center,
                    radius = ZONE_RADIUS_METERS,
                    fillColor = color.copy(alpha = 0.25f),
                    strokeColor = color,
                    strokeWidth = 3f,
                    clickable = true,
                    onClick = { _ -> viewModel.onZoneSelected(zone) }
                )

                // Custom label marker with disease name + accuracy + count
                val labelIcon = remember(zone.disease, zone.avgAccuracy, zone.count) {
                    diseaseLabelIcon(
                        disease = zone.disease,
                        accuracy = zone.avgAccuracy,
                        count = zone.count,
                        color = color
                    )
                }
                Marker(
                    state = MarkerState(position = zone.center),
                    icon = labelIcon,
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        viewModel.onZoneSelected(zone)
                        true
                    }
                )
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // ── "My Location" recenter button ────────────────────────────────
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
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "My Location",
                tint = Color.White
            )
        }

        // ── Empty state ──────────────────────────────────────────────────
        if (!uiState.isLoading && uiState.zones.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "No disease reports in your area yet.\nTalk to Saathi about your symptoms to add one!",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // ── Bottom sheet for selected zone ────────────────────────────────────────
    val selectedZone = uiState.selectedZone
    if (selectedZone != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val color = zoneColor(selectedZone.count)

        ModalBottomSheet(
            onDismissRequest = { viewModel.onZoneSelected(null) },
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
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header: Disease name + badges ────────────────────────
                item {
                    Column {
                        Text(
                            text = selectedZone.disease,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(Modifier.height(10.dp))

                        // Alert level
                        val alertLevel = if (selectedZone.count > 10) "⚠️ High Alert Zone" else "🟡 Active Zone"
                        Text(
                            text = alertLevel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZoneBadge(
                                text = "${selectedZone.avgAccuracy}% avg confidence",
                                color = color
                            )
                            ZoneBadge(
                                text = "${selectedZone.count} report${if (selectedZone.count > 1) "s" else ""}",
                                color = color
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // All unique symptoms across reports
                        val allSymptoms = selectedZone.reports.flatMap { it.symptoms }.distinct()
                        if (allSymptoms.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allSymptoms) { symptom ->
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
                        }
                    }
                }

                // ── Divider ──────────────────────────────────────────────
                item {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }

                // ── Individual reports ────────────────────────────────────
                item {
                    Text(
                        text = "Individual Reports",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                items(selectedZone.reports) { report ->
                    ReportCard(report = report, zoneColor = color)
                }

                // ── Footer ───────────────────────────────────────────────
                item {
                    Text(
                        text = "All reports are anonymous • Community health data",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.25f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Reusable composables ─────────────────────────────────────────────────────

@Composable
private fun ZoneBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReportCard(report: DiseaseReport, zoneColor: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.disease,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                ZoneBadge(text = "${report.accuracy}%", color = zoneColor)
            }

            if (report.symptoms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = report.symptoms.joinToString(" • "),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            if (report.reasoning.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = report.reasoning,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = relativeTime(report.timestamp),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}
