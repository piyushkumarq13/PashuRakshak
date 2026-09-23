package com.pashurakshak.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object ForgotPin : Screen("forgot_pin")
    data object Onboarding : Screen("onboarding")
    data object FarmerHome : Screen("farmer_home")
    data object VetHome : Screen("vet_home")

    data object ReportSickAnimal : Screen("report_sick_animal")
    data object MyAnimals : Screen("my_animals")
    data object VaccinationStatus : Screen("vaccination_status")
    data object Alerts : Screen("alerts")
    data object VetCaseQueue : Screen("vet_case_queue")
    data object VetAlerts : Screen("vet_alerts")
    data object MyReports : Screen("my_reports")

    data object CaseDetail : Screen("case_detail/{reportId}/{scanQrAuto}") {
        const val ARG_REPORT_ID = "reportId"
        const val ARG_SCAN_QR_AUTO = "scanQrAuto"

        fun withReportId(reportId: String, scanQrAuto: Boolean = false): String =
            "case_detail/$reportId/$scanQrAuto"
    }

    data object QrPassport : Screen("qr_passport/{animalId}") {
        const val ARG_ANIMAL_ID = "animalId"

        fun withAnimalId(animalId: String): String = "qr_passport/$animalId"
    }

    data object B2UploadTest : Screen("b2_upload_test")
    data object AiInsight : Screen("ai_insight/{reportId}/{aiAdvisory}") {
        const val ARG_REPORT_ID = "reportId"
        const val ARG_AI_ADVISORY = "aiAdvisory"

        fun withReportId(reportId: String, aiAdvisory: String? = null): String =
            "ai_insight/$reportId/${Uri.encode(aiAdvisory ?: "")}"
    }
}
