package com.pashurakshak.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object PhoneEntry : Screen("phone_entry")
    data object RoleSelect : Screen("role_select")
    data object FarmerHome : Screen("farmer_home")
    data object VetHome : Screen("vet_home")

    data object Otp : Screen("otp/{verificationId}/{phone}") {
        const val ARG_VERIFICATION_ID = "verificationId"
        const val ARG_PHONE = "phone"

        fun withArgs(verificationId: String, phone: String): String =
            "otp/${Uri.encode(verificationId)}/${Uri.encode(phone)}"
    }

    data object ReportSickAnimal : Screen("report_sick_animal")
    data object MyAnimals : Screen("my_animals")
    data object VaccinationStatus : Screen("vaccination_status")
    data object Alerts : Screen("alerts")
    data object VetCaseQueue : Screen("vet_case_queue")
    data object VetAlerts : Screen("vet_alerts")
    data object MyReports : Screen("my_reports")

    data object CaseDetail : Screen("case_detail/{reportId}") {
        const val ARG_REPORT_ID = "reportId"

        fun withReportId(reportId: String): String = "case_detail/$reportId"
    }

    data object QrPassport : Screen("qr_passport/{animalId}") {
        const val ARG_ANIMAL_ID = "animalId"

        fun withAnimalId(animalId: String): String = "qr_passport/$animalId"
    }

    data object B2UploadTest : Screen("b2_upload_test")
}
