package com.sohanreddy.sevak.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.sohanreddy.sevak.data.PrefsManager
import com.sohanreddy.sevak.data.rag.VectorStoreManager
import com.sohanreddy.sevak.ui.auth.OtpScreen
import com.sohanreddy.sevak.ui.auth.PhoneEntryScreen
import com.sohanreddy.sevak.ui.main.AuthenticatedAppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Routes {
    const val PHONE = "phone"
    const val OTP = "otp"
    const val APP = "app"
}

@Composable
fun SaathiNavGraph(
    navController: NavHostController,
    prefs: PrefsManager,
    application: android.app.Application
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDest = if (currentUser == null) Routes.PHONE else Routes.APP

    var verificationId by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }

    NavHost(navController = navController, startDestination = startDest) {
        composable(Routes.PHONE) {
            PhoneEntryScreen(
                onOtpSent = { vId, phone, token ->
                    verificationId = vId
                    phoneNumber = phone
                    resendToken = token
                    navController.navigate(Routes.OTP)
                }
            )
        }

        composable(Routes.OTP) {
            OtpScreen(
                verificationId = verificationId,
                phone = phoneNumber,
                resendToken = resendToken,
                onVerified = {
                    navController.navigate(Routes.APP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.APP) {
            AuthenticatedAppShell(
                prefs = prefs,
                onSignOut = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            VectorStoreManager.deleteAllForUser(uid)
                        }
                    }
                    FirebaseAuth.getInstance().signOut()
                    prefs.clear()
                    navController.navigate(Routes.PHONE) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
