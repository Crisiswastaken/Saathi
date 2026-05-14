package com.sohanreddy.sevak

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sohanreddy.sevak.data.PrefsManager
import com.sohanreddy.sevak.navigation.Routes
import com.sohanreddy.sevak.navigation.SaathiNavGraph
import com.sohanreddy.sevak.ui.theme.SaathiTheme
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohanreddy.sevak.ui.main.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!isOnline()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()
        }

        val prefs = PrefsManager(this)
        val app = application

        setContent {
            SaathiTheme {
                val navController = rememberNavController()
                val mainViewModel: MainViewModel = viewModel()
                val navigateToMap by mainViewModel.symptomViewModel.navigateToMap
                    .collectAsStateWithLifecycle()

                // Navigate to map when symptom flow confirms
                LaunchedEffect(navigateToMap) {
                    if (navigateToMap) {
                        navController.navigate(Routes.DISEASE_MAP)
                        mainViewModel.symptomViewModel.resetNavigation()
                    }
                }

                SaathiNavGraph(
                    navController = navController,
                    prefs = prefs,
                    application = app
                )
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
