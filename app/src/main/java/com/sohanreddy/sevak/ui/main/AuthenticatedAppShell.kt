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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sohanreddy.sevak.R
import com.sohanreddy.sevak.data.PrefsManager
import com.sohanreddy.sevak.ui.map.DiseaseMapScreen
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
        val placeholderItems = listOf(Pulse, Me)

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
    val currentDestination = AppDestination.fromRoute(navBackStackEntry?.destination?.route)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SaathiBottomDock(
                items = AppDestination.dockItems,
                currentRoute = currentDestination.route,
                onDestinationSelected = { destination ->
                    if (destination.route == currentDestination.route) {
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
                    contentPadding = innerPadding
                )
            }

            composable(AppDestination.Radar.route) {
                DiseaseMapScreen(
                    contentPadding = innerPadding
                )
            }

            AppDestination.placeholderItems.forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(
                        destination = destination,
                        contentPadding = innerPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    contentPadding: PaddingValues,
    onDestinationSelected: (AppDestination) -> Unit
) {
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(60_000)
            value = LocalDateTime.now()
        }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMMM d, EEEE", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }

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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Home",
                        color = Color(0xFF10233F),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = now.format(dateFormatter),
                        color = Color(0xFF22385B),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 12.dp,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Current time",
                            tint = Color(0xFF566983),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = now.format(timeFormatter),
                            color = Color(0xFF566983),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
private fun PlaceholderScreen(
    destination: AppDestination,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDFEFF), Color(0xFFF6FBFF), Color(0xFFF7F9FF))
                )
            )
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.78f),
            tonalElevation = 2.dp,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Image(
                    painter = painterResource(destination.heroRes),
                    contentDescription = destination.label,
                    contentScale = if (destination == AppDestination.Me) ContentScale.Fit else ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (destination == AppDestination.Me) {
                                Modifier.heightIn(max = 220.dp)
                            } else {
                                Modifier.aspectRatio(destination.heroAspectRatio)
                            }
                        )
                        .clip(RoundedCornerShape(26.dp))
                )

                Text(
                    text = destination.label,
                    color = Color(0xFF122543),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = destination.subtitle,
                    color = destination.accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "This section is ready for future features. The shared shell and dock navigation are in place, so extending it now is straightforward.",
                    color = Color(0xFF5A6E88),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                Text(
                    text = "Coming soon",
                    color = Color(0xFFEE5E80),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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