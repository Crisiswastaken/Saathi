package com.sohanreddy.sevak.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.sohanreddy.sevak.R
import com.sohanreddy.sevak.data.PrefsManager
import com.sohanreddy.sevak.ui.map.DiseaseMapScreen
import com.sohanreddy.sevak.ui.pulse.PulseScreen
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class AppDestination(
    val route: String,
    val label: String,
    @DrawableRes val dockIconRes: Int,
    @DrawableRes val heroRes: Int,
    val heroAspectRatio: Float,
    val accentColor: Color,
    val subtitle: String,
    val isDashboardCard: Boolean = false
) {
    companion object {
        val Home = AppDestination(
            route = "home",
            label = "Home",
            dockIconRes = R.drawable.dock_home,
            heroRes = R.drawable.section_saathi_card,
            heroAspectRatio = 874f / 411f,
            accentColor = Color(0xFFFA6B89),
            subtitle = "Your health overview"
        )
        val Pulse = AppDestination(
            route = "pulse",
            label = "Pulse",
            dockIconRes = R.drawable.dock_pulse,
            heroRes = R.drawable.section_pulse_card,
            heroAspectRatio = 1903f / 826f,
            accentColor = Color(0xFF5D8DFF),
            subtitle = "Smart health tracking",
            isDashboardCard = true
        )
        val Saathi = AppDestination(
            route = "saathi",
            label = "Saathi",
            dockIconRes = R.drawable.dock_saathi,
            heroRes = R.drawable.section_saathi_card,
            heroAspectRatio = 874f / 411f,
            accentColor = Color(0xFFF65987),
            subtitle = "Your AI health companion",
            isDashboardCard = true
        )
        val Radar = AppDestination(
            route = "radar",
            label = "Radar",
            dockIconRes = R.drawable.dock_radar,
            heroRes = R.drawable.section_radar_card,
            heroAspectRatio = 1815f / 736f,
            accentColor = Color(0xFF20B79B),
            subtitle = "Health insights nearby",
            isDashboardCard = true
        )
        val Me = AppDestination(
            route = "me",
            label = "Me",
            dockIconRes = R.drawable.dock_me,
            heroRes = R.drawable.dock_me,
            heroAspectRatio = 135f / 221f,
            accentColor = Color(0xFF8FA1BD),
            subtitle = "Personal profile"
        )

        val dockItems = listOf(Home, Pulse, Saathi, Radar, Me)
        val dashboardCards = listOf(Saathi, Pulse, Radar)
        val placeholderItems = emptyList<AppDestination>()

        fun fromRoute(route: String?): AppDestination {
            return dockItems.firstOrNull { it.route == route } ?: Home
        }
    }
}

@Composable
fun AuthenticatedAppShell(
    prefs: PrefsManager,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDestination = AppDestination.fromRoute(currentRoute)
    // Show dock on all screens (sub-screens like documents/reports too)
    val showDock = currentRoute != null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showDock) {
                SaathiBottomDock(
                    items = AppDestination.dockItems,
                    currentRoute = currentDestination.route,
                    onDestinationSelected = { destination ->
                        // Only skip if we're literally on that dock item's route already
                        if (destination.route == currentRoute) {
                            return@SaathiBottomDock
                        }
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(AppDestination.Home.route) {
                DashboardScreen(
                    contentPadding = innerPadding,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(AppDestination.Saathi.route) {
                MainScreen(
                    prefs = prefs,
                    onSignOut = onSignOut,
                    contentPadding = innerPadding,
                    onOpenDocuments = { navController.navigate("saathi_documents") },
                    onOpenReports = { navController.navigate("saathi_reports") }
                )
            }

            composable("saathi_documents") {
                DocumentManagerScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("saathi_reports") {
                ReportManagerScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onCreateReport = { navController.navigate("saathi_report_editor/new") },
                    onEditReport = { reportId -> navController.navigate("saathi_report_editor/$reportId") }
                )
            }

            composable("saathi_report_editor/{reportId}") { entry ->
                ReportEditorScreen(
                    reportId = entry.arguments?.getString("reportId"),
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppDestination.Pulse.route) {
                PulseScreen(
                    contentPadding = innerPadding
                )
            }

            composable(AppDestination.Radar.route) {
                DiseaseMapScreen(
                    contentPadding = innerPadding
                )
            }

            composable(AppDestination.Me.route) {
                MeScreen(
                    contentPadding = innerPadding,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    contentPadding: PaddingValues,
    onDestinationSelected: (AppDestination) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFDFEFF),
                        Color(0xFFF7FAFF),
                        Color(0xFFFDF7FB)
                    )
                )
            )
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 18.dp,
            end = 24.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to",
                    color = Color(0xFF8FA1BD),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Saathi",
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF5D8DFF), Color(0xFFF65987))
                        )
                    ),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }

        items(AppDestination.dashboardCards) { destination ->
            DashboardCard(
                destination = destination,
                onClick = { onDestinationSelected(destination) }
            )
        }
    }
}

@Composable
private fun DashboardCard(
    destination: AppDestination,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
        tonalElevation = 2.dp,
        shadowElevation = 14.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            Image(
                painter = painterResource(destination.heroRes),
                contentDescription = destination.label,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(destination.heroAspectRatio)
            )
        }
    }
}

@Composable
private fun MeScreen(
    contentPadding: PaddingValues,
    onSignOut: () -> Unit
) {
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val phoneNumber = user?.phoneNumber ?: "Not available"
    var showPolicy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFDFEFF),
                        Color(0xFFF7FAFF),
                        Color(0xFFFDF7FB)
                    )
                )
            )
            .padding(contentPadding)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Header ──
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Me",
                color = Color(0xFF10233F),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Your profile & preferences",
                color = Color(0xFF566983),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Profile card ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar circle
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = Color(0xFFEDF3FF),
                    border = BorderStroke(2.dp, Color(0xFFD4E2F7))
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF5D8DFF),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Phone number row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF4F8FF),
                    border = BorderStroke(1.dp, Color(0xFFE3ECF8))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF5D8DFF),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Phone number",
                                color = Color(0xFF566983),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = phoneNumber,
                                color = Color(0xFF10233F),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sign out button
                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF65987),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Sign Out",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Privacy Policy ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPolicy = !showPolicy },
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.76f)),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF20B79B),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Privacy Policy",
                        color = Color(0xFF10233F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showPolicy) {
                    HorizontalDivider(color = Color(0xFFE7EEF9))

                    Text(
                        text = "Saathi Privacy Policy",
                        color = Color(0xFF10233F),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Last updated: May 2026",
                        color = Color(0xFF566983),
                        fontSize = 12.sp
                    )

                    val policyText = """
1. Information We Collect
Saathi collects your phone number for authentication purposes only. Health measurements (heart rate, SpO₂) are processed entirely on your device and are never uploaded to external servers. Voice interactions are processed through third-party APIs (Groq, Sarvam) using encrypted connections, and audio recordings are not stored after processing.

2. How We Use Your Data
Your health data is stored locally on your device to display measurement history and generate wellness reports. Location data is used solely for the disease prevalence radar feature and is anonymized before submission. We do not sell, trade, or share your personal information with third parties for marketing purposes.

3. Data Storage & Security
All health records, documents, and reports are stored in your device's local storage. Firebase Authentication secures your account access. We employ industry-standard encryption for all network communications. You may delete all local data at any time by clearing the app's storage.

4. Third-Party Services
Saathi integrates with Google Firebase (authentication), Groq (AI processing), and Sarvam AI (speech processing). Each service maintains its own privacy practices. We recommend reviewing their respective privacy policies for complete transparency.

5. Your Rights
You have the right to access, correct, or delete your personal data at any time. Signing out removes your authentication credentials. Uninstalling the app removes all locally stored health data permanently.

6. Medical Disclaimer
Saathi is a wellness estimation tool only. Measurements are not clinically validated and should not replace professional medical diagnosis. Always consult a qualified healthcare provider for medical concerns.

7. Contact
For questions about this privacy policy, reach out through the app's feedback channels or contact the development team at the project repository.
                    """.trimIndent()

                    Text(
                        text = policyText,
                        color = Color(0xFF566983),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        text = "Tap to view our privacy policy",
                        color = Color(0xFF8FA1BD),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── Footer ──
        Text(
            text = "Saathi v1.0 • Built with care",
            color = Color(0xFFB0BDD0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SaathiBottomDock(
    items: List<AppDestination>,
    currentRoute: String,
    onDestinationSelected: (AppDestination) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            shadowElevation = 20.dp,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onDestinationSelected(item) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(item.dockIconRes),
                            contentDescription = item.label,
                            modifier = Modifier
                                .height(64.dp)
                                .alpha(if (item.route == currentRoute) 1f else 0.44f),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
