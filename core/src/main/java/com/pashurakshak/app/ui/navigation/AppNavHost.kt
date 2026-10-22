package com.pashurakshak.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.navigation.Screen
import com.pashurakshak.app.notifications.AppNotifications
import com.pashurakshak.app.ui.FarmerHomeScreen
import com.pashurakshak.app.ui.OnboardingScreen
import com.pashurakshak.app.ui.VetHomeScreen
import com.pashurakshak.app.ui.aiInsight.AiChatScreen
import com.pashurakshak.app.ui.aiInsight.AiInsightScreen
import com.pashurakshak.app.ui.auth.ForgotPinScreen
import com.pashurakshak.app.ui.auth.LoginScreen
import com.pashurakshak.app.ui.auth.RegisterScreen
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
import com.pashurakshak.app.ui.profile.ProfileScreen
import com.pashurakshak.app.ui.vet.CaseDetailScreen
import com.pashurakshak.app.ui.vet.VetCaseQueueScreen

// Full-screen routes with no bottom chrome (auth, detail & tool pages reached
// with a back button). Every other route gets the floating bar, so new tab
// pages get it automatically instead of having to be registered here.
private val NoBarRoutes = setOf(
    Screen.Login.route,
    Screen.Register.route,
    Screen.ForgotPin.route,
    Screen.Onboarding.route,
    Screen.QrPassport.route,
    Screen.CaseDetail.route,
    Screen.AiInsight.route,
    Screen.AiChat.route,
    Screen.B2UploadTest.route,
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationShown: () -> Unit = {},
) {
    // Already signed in (with a backend session) skips the login flow entirely.
    val startDestination = remember {
        when {
            !SessionManager.isLoggedIn -> Screen.Login.route
            SessionManager.role == SessionManager.Role.VET -> Screen.VetHome.route
            SessionManager.profileComplete -> Screen.FarmerHome.route
            else -> Screen.Onboarding.route
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncStatusBanner()
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(WindowInsets.statusBars),
            ) {
            // Auth: login / register / forgot-PIN
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoggedIn = { needsOnboarding ->
                        val destination = when {
                            SessionManager.role == SessionManager.Role.VET -> Screen.VetHome
                            needsOnboarding -> Screen.Onboarding
                            else -> Screen.FarmerHome
                        }
                        navController.navigate(destination.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onGoRegister = { navController.navigate(Screen.Register.route) },
                    onGoForgotPin = { navController.navigate(Screen.ForgotPin.route) },
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegistered = {
                        navController.navigate(Screen.FarmerHome.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.ForgotPin.route) {
                ForgotPinScreen(
                    onCompleted = { needsOnboarding ->
                        val destination = when {
                            SessionManager.role == SessionManager.Role.VET -> Screen.VetHome
                            needsOnboarding -> Screen.Onboarding
                            else -> Screen.FarmerHome
                        }
                        navController.navigate(destination.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Onboarding (legacy farmers without a backend profile row)
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onSubmitComplete = {
                        navController.navigate(Screen.FarmerHome.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                )
            }

            // Farmer
            composable(Screen.FarmerHome.route) {
                FarmerHomeScreen(
                    onOpenMyReports = { filter ->
                        navController.navigate("${Screen.MyReports.route}?filter=$filter")
                    },
                    onOpenAnimals = { navController.navigate(Screen.MyAnimals.route) },
                    onOpenReport = { navController.navigate(Screen.ReportSickAnimal.route) },
                    onOpenVaccination = { navController.navigate(Screen.VaccinationStatus.route) },
                    onOpenAlerts = { navController.navigate(Screen.Alerts.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                    onOpenQrScan = { navController.navigate(Screen.MyAnimals.route) },
                    onOpenB2Test = { navController.navigate(Screen.B2UploadTest.route) },
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.MyAnimals.route) {
                MyAnimalsScreen(
                    onAnimalClick = { animalId ->
                        navController.navigate(Screen.QrPassport.withAnimalId(animalId))
                    },
                )
            }
            composable(Screen.AiChat.route) {
                AiChatScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.ReportSickAnimal.route) {
                ReportSickAnimalScreen(
                    onSubmitted = { _, _ -> },
                    navController = navController,
                )
            }
            composable(Screen.VaccinationStatus.route) {
                VaccinationStatusScreen(
                )
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(
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
                    onOpenCaseQueue = { filter ->
                        navController.navigate("${Screen.VetCaseQueue.route}?filter=$filter")
                    },
                    onOpenAlerts = { navController.navigate(Screen.VetAlerts.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                )
            }
            composable(
                route = "${Screen.VetCaseQueue.route}?filter={filter}",
                arguments = listOf(
                    androidx.navigation.navArgument("filter") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = "ongoing"
                    },
                ),
            ) { entry ->
                val filterName = entry.arguments?.getString("filter") ?: "ongoing"
                val filter = when (filterName) {
                    "examined" -> com.pashurakshak.app.ui.vet.VetCaseQueueViewModel.QueueFilter.EXAMINED
                    "high_risk" -> com.pashurakshak.app.ui.vet.VetCaseQueueViewModel.QueueFilter.HIGH_RISK
                    "resolved" -> com.pashurakshak.app.ui.vet.VetCaseQueueViewModel.QueueFilter.RESOLVED
                    else -> com.pashurakshak.app.ui.vet.VetCaseQueueViewModel.QueueFilter.ONGOING
                }
                VetCaseQueueScreen(
                    filter = filter,
                    onReportClick = { reportId ->
                        navController.navigate(Screen.CaseDetail.withReportId(reportId)) {
                            launchSingleTop = true
                        }
                    },
                    onQrScanClick = { reportId ->
                        navController.navigate(Screen.CaseDetail.withReportId(reportId, scanQrAuto = true)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Screen.CaseDetail.route,
                arguments = listOf(
                    navArgument(Screen.CaseDetail.ARG_REPORT_ID) { type = NavType.StringType },
                    navArgument(Screen.CaseDetail.ARG_SCAN_QR_AUTO) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val scanQrAuto = entry.arguments?.getBoolean(Screen.CaseDetail.ARG_SCAN_QR_AUTO) ?: false
                CaseDetailScreen(
                    reportId = entry.arguments?.getString(Screen.CaseDetail.ARG_REPORT_ID).orEmpty(),
                    onBack = { navController.popBackStack() },
                    scanQrAuto = scanQrAuto,
                )
            }
            composable(Screen.VetAlerts.route) {
                // Role-wide: every vet sees high-risk broadcasts.
                AlertsScreen(
                    recipientRole = "vet",
                    recipientId = null,
                )
            }
            composable(
                route = "${Screen.MyReports.route}?filter={filter}",
                arguments = listOf(
                    androidx.navigation.navArgument("filter") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = "all"
                    },
                ),
            ) { entry ->
                MyReportsScreen(
                    filter = entry.arguments?.getString("filter") ?: "all",
                    onReportClick = { reportId ->
                        navController.navigate(Screen.AiInsight.withReportId(reportId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = Screen.AiInsight.route,
                arguments = listOf(
                    navArgument(Screen.AiInsight.ARG_REPORT_ID) { type = NavType.StringType },
                    navArgument(Screen.AiInsight.ARG_AI_ADVISORY) { type = NavType.StringType },
                ),
            ) { entry ->
                val reportId = entry.arguments?.getString(Screen.AiInsight.ARG_REPORT_ID).orEmpty()
                val aiAdvisory = entry.arguments?.getString(Screen.AiInsight.ARG_AI_ADVISORY)?.let {
                    if (it.isBlank()) null else it
                }
                AiInsightScreen(
                    reportId = reportId,
                    aiAdvisory = aiAdvisory,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.B2UploadTest.route) {
                B2UploadTestScreen(onBack = { navController.popBackStack() })
            }
        }
        }

        if (currentRoute != null && currentRoute !in NoBarRoutes) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            ) {
                when (SessionManager.role) {
                    SessionManager.Role.FARMER -> FarmerBottomBar(navController)
                    SessionManager.Role.VET -> VetBottomBar(navController)
                    null -> Unit
                }
            }
        }
    }
}

