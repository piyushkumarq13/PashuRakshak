package com.pashurakshak.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.navigation.Screen
import com.pashurakshak.app.notifications.AppNotifications
import com.pashurakshak.app.ui.FarmerHomeScreen
import com.pashurakshak.app.ui.RoleSelectScreen
import com.pashurakshak.app.ui.VetHomeScreen
import com.pashurakshak.app.ui.auth.OtpScreen
import com.pashurakshak.app.ui.auth.PhoneEntryScreen
import com.pashurakshak.app.ui.components.FarmerBottomBar
import com.pashurakshak.app.ui.components.SyncStatusBanner
import com.pashurakshak.app.ui.components.VetBottomBar
import com.pashurakshak.app.ui.debug.B2UploadTestScreen
import com.pashurakshak.app.ui.farmer.AlertsScreen
import com.pashurakshak.app.ui.farmer.MyAnimalsScreen
import com.pashurakshak.app.ui.farmer.MyReportsScreen
import com.pashurakshak.app.ui.farmer.QrPassportScreen
import com.pashurakshak.app.ui.farmer.ReportSickAnimalScreen
import com.pashurakshak.app.ui.farmer.VaccinationStatusScreen
import com.pashurakshak.app.ui.vet.CaseDetailScreen
import com.pashurakshak.app.ui.vet.VetCaseQueueScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationShown: () -> Unit = {},
) {
    // Already signed in (with a role) skips the login flow entirely.
    val startDestination = remember {
        when (SessionManager.role) {
            SessionManager.Role.FARMER -> Screen.FarmerHome.route
            SessionManager.Role.VET -> Screen.VetHome.route
            null -> Screen.PhoneEntry.route
        }
    }

    // Notification tap → Alerts screen (farmer or vet variant).
    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            AppNotifications.DESTINATION_ALERTS -> {
                if (SessionManager.isLoggedIn) {
                    val route = when (SessionManager.role) {
                        SessionManager.Role.FARMER -> Screen.Alerts.route
                        SessionManager.Role.VET -> Screen.VetAlerts.route
                        null -> null
                    }
                    route?.let {
                        navController.navigate(it) { launchSingleTop = true }
                    }
                }
                onPendingDestinationShown()
            }

            null -> Unit
            else -> onPendingDestinationShown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SyncStatusBanner()
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
        ) {
            // Auth: phone → OTP → role → home
            composable(Screen.PhoneEntry.route) {
                PhoneEntryScreen(
                    onCodeSent = { verificationId, phoneE164 ->
                        navController.navigate(Screen.Otp.withArgs(verificationId, phoneE164))
                    },
                    onVerified = {
                        navController.navigate(Screen.RoleSelect.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Screen.Otp.route,
                arguments = listOf(
                    navArgument(Screen.Otp.ARG_VERIFICATION_ID) { type = NavType.StringType },
                    navArgument(Screen.Otp.ARG_PHONE) { type = NavType.StringType },
                ),
            ) { entry ->
                OtpScreen(
                    verificationId = entry.arguments?.getString(Screen.Otp.ARG_VERIFICATION_ID).orEmpty(),
                    phoneE164 = entry.arguments?.getString(Screen.Otp.ARG_PHONE).orEmpty(),
                    onVerified = {
                        navController.navigate(Screen.RoleSelect.route) {
                            launchSingleTop = true
                        }
                    },
                    onChangeNumber = { navController.popBackStack() },
                )
            }
            composable(Screen.RoleSelect.route) {
                RoleSelectScreen(
                    onRoleSelected = { role ->
                        SessionManager.completeLogin(role)
                        val uid = SessionManager.uid.orEmpty()
                        val phone = SessionManager.phone.orEmpty()
                        if (uid.isNotEmpty()) {
                            // Application-scoped so navigation can't cancel the registration.
                            GlobalScope.launch(
                                Dispatchers.IO + NonCancellable,
                            ) {
                                runCatching {
                                    ServiceLocator.authRepository.saveRoleMapping(uid, phone, role)
                                }
                            }
                        }
                        val destination = when (role) {
                            SessionManager.Role.FARMER -> Screen.FarmerHome
                            SessionManager.Role.VET -> Screen.VetHome
                        }
                        navController.navigate(destination.route) {
                            popUpTo(Screen.PhoneEntry.route) { inclusive = true }
                        }
                    },
                )
            }

            // Farmer
            composable(Screen.FarmerHome.route) {
                FarmerHomeScreen(
                    onOpenMyReports = { navController.navigate(Screen.MyReports.route) },
                    onOpenB2Test = { navController.navigate(Screen.B2UploadTest.route) },
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(Screen.MyAnimals.route) {
                MyAnimalsScreen(
                    onAnimalClick = { animalId ->
                        navController.navigate(Screen.QrPassport.withAnimalId(animalId))
                    },
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(Screen.ReportSickAnimal.route) {
                ReportSickAnimalScreen(
                    onSubmitted = { navController.popBackStack() },
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(Screen.VaccinationStatus.route) {
                VaccinationStatusScreen(
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(
                route = Screen.QrPassport.route,
                arguments = listOf(
                    navArgument(Screen.QrPassport.ARG_ANIMAL_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                QrPassportScreen(
                    animalId = entry.arguments?.getString(Screen.QrPassport.ARG_ANIMAL_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            // Vet
            composable(Screen.VetHome.route) {
                VetHomeScreen(
                    bottomBar = { VetBottomBar(navController) },
                )
            }
            composable(Screen.VetCaseQueue.route) {
                VetCaseQueueScreen(
                    onReportClick = { reportId ->
                        navController.navigate(Screen.CaseDetail.withReportId(reportId))
                    },
                    bottomBar = { VetBottomBar(navController) },
                )
            }
            composable(
                route = Screen.CaseDetail.route,
                arguments = listOf(
                    navArgument(Screen.CaseDetail.ARG_REPORT_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                CaseDetailScreen(
                    reportId = entry.arguments?.getString(Screen.CaseDetail.ARG_REPORT_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.VetAlerts.route) {
                // Role-wide: every vet sees high-risk broadcasts.
                AlertsScreen(
                    recipientRole = "vet",
                    recipientId = null,
                    bottomBar = { VetBottomBar(navController) },
                )
            }
            composable(Screen.MyReports.route) {
                MyReportsScreen(
                    bottomBar = { FarmerBottomBar(navController) },
                )
            }
            composable(Screen.B2UploadTest.route) {
                B2UploadTestScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
