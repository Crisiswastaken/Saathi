package com.sohanreddy.sevak.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Folder

import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.sohanreddy.sevak.R
import com.sohanreddy.sevak.data.PrefsManager
import com.sohanreddy.sevak.data.getStatusText
import com.sohanreddy.sevak.data.supportedLanguages
import com.sohanreddy.sevak.screenshare.ScreenShareService
import com.sohanreddy.sevak.ui.theme.SaathiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: PrefsManager,
    viewModel: MainViewModel = viewModel(),
    onSignOut: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenDocuments: () -> Unit = {},
    onOpenReports: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    val waveformInteraction = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val activity = context as? Activity
    val projectionManager = remember(context) { context.getSystemService(MediaProjectionManager::class.java) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val projectionData = result.data
        if (result.resultCode != Activity.RESULT_OK || projectionData == null) {
            Toast.makeText(context, "Screen sharing permission denied", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val serviceIntent = Intent(context, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START
            putExtra(ScreenShareService.EXTRA_RESULT_CODE, result.resultCode)
            putExtra(ScreenShareService.EXTRA_RESULT_DATA, projectionData)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        Toast.makeText(context, "Screen sharing started", Toast.LENGTH_SHORT).show()
        activity?.moveTaskToBack(true)
    }

    val launchProjectionRequest: () -> Unit = {
        val manager = projectionManager
        if (manager == null) {
            Toast.makeText(context, "Screen capture is unavailable on this device", Toast.LENGTH_SHORT).show()
        } else {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
            mediaProjectionLauncher.launch(intent)
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            launchProjectionRequest()
        } else {
            Toast.makeText(context, "Overlay permission is required for floating bubble", Toast.LENGTH_SHORT).show()
        }
    }

    // Use detected language from state, or saved language from prefs, or default to "en"
    val currentLangCode = state.detectedLangCode
        ?: prefs.getLanguageCode()
        ?: "en"
    var selectedLangCode by remember(currentLangCode) { mutableStateOf(currentLangCode) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val selectedLanguage = supportedLanguages.firstOrNull { it.code == selectedLangCode }
        ?: supportedLanguages.firstOrNull { it.code == "en" }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicTap()
        } else {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    val screenShareAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        if (Settings.canDrawOverlays(context)) {
            launchProjectionRequest()
        } else {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(overlayIntent)
        }
    }

    // ── Location permission ──────────────────────────────────────────────
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.fetchUserLocation()
        // If denied, location stays null — map submission is silently skipped
    }

    // Request location permission once on first composition
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // If already granted, MainViewModel.init already called fetchUserLocation()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ── Background image ────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.10f),
                            Color(0xFFFDF7FB).copy(alpha = 0.42f)
                        )
                    )
                )
        )

        // ── Settings icon — top right ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 24.dp, top = 18.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Saathi",
                    color = Color(0xFF10233F),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = selectedLanguage?.englishName ?: "English",
                    color = Color(0xFF566983),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
                shadowElevation = 12.dp,
                tonalElevation = 2.dp
            ) {
                IconButton(onClick = { showSheet = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF566983),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Waveform centred content ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clickable waveform area
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .height(132.dp)
                    .clickable(
                        interactionSource = waveformInteraction,
                        indication = null
                    ) {
                        if (viewModel.hasAudioPermission()) {
                            viewModel.onMicTap()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                WaveformCanvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    isActive = state.assistantState == AssistantState.LISTENING ||
                            state.assistantState == AssistantState.SPEAKING,
                    isStatic = state.assistantState == AssistantState.PROCESSING,
                    amplitude = state.audioAmplitude
                )
            }

            Spacer(Modifier.height(20.dp))

            // Status text
            Text(
                text = getStatusText(state.assistantState.name, currentLangCode),
                color = when (state.assistantState) {
                    AssistantState.LISTENING -> Color(0xFF5D8DFF)
                    AssistantState.SPEAKING -> Color(0xFFF65987)
                    AssistantState.PROCESSING -> Color(0xFF20B79B)
                    else -> Color(0xFF6F7FA0)
                },
                fontSize = 16.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // ── Generated Image Overlay ──────────────────────────────────
        val generatedImage = state.generatedImage
        if (generatedImage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clickable { viewModel.clearGeneratedImage() },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = generatedImage.asImageBitmap(),
                            contentDescription = "AI generated image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap to dismiss",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // ── Settings bottom sheet ───────────────────────────────────
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                sheetState = sheetState,
                containerColor = Color.White.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 8.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD9E4F5))
                    )
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        "Saathi settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10233F),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Language selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLanguage?.let { "${it.displayName}  ${it.englishName}" } ?: "English",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Conversation language") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { languageMenuExpanded = !languageMenuExpanded }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { languageMenuExpanded = !languageMenuExpanded },
                            shape = RoundedCornerShape(18.dp)
                        )

                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            supportedLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.displayName}  ${lang.englishName}") },
                                    onClick = {
                                        selectedLangCode = lang.code
                                        languageMenuExpanded = false
                                        viewModel.setLanguageManually(lang.code, lang.englishName)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Spacer(Modifier.height(2.dp))
                    SettingsSheetCard(
                        title = "Upload documents",
                        body = "Manage prescriptions, lab files, and notes stored on this device.",
                        accent = Color(0xFF5D8DFF),
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showSheet = false
                            onOpenDocuments()
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    SettingsSheetCard(
                        title = "Report viewer",
                        body = "Create, view, edit, and delete local health summary reports.",
                        accent = Color(0xFFF65987),
                        icon = { Icon(Icons.Default.Article, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showSheet = false
                            onOpenReports()
                        }
                    )

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsSheetCard(
    title: String,
    body: String,
    accent: Color,
    icon: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF7FAFF),
        border = BorderStroke(1.dp, Color(0xFFE3ECF8)),
        shadowElevation = 4.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(accent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
                content = icon
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = Color(0xFF10233F),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    body,
                    color = Color(0xFF566983),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
