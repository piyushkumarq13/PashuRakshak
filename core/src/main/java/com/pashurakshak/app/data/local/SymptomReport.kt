package com.pashurakshak.app.data.local

import java.util.UUID

enum class ReportStatus(val dbValue: String) {
    REPORTED("reported"),
    VET_ASSIGNED("vet_assigned"),
    EXAMINED("examined"),
    SAMPLE_SENT("sample_sent"),
    CONFIRMED("confirmed"),
    RESOLVED("resolved");

    companion object {
        fun fromDbValue(value: String): ReportStatus =
            entries.firstOrNull { it.dbValue == value } ?: REPORTED
    }
}

data class SymptomReport(
    val id: String = UUID.randomUUID().toString(),
    val animalId: String,
    val farmerId: String,
    val symptoms: List<String>,
    val photoLocalPath: String,
    val photoRemoteUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val riskScore: Int,
    val riskBreakdown: Map<String, Int>,
    val status: ReportStatus = ReportStatus.REPORTED,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val village: String? = null,
    val aiAdvisory: String? = null,
    val assignedVetId: String? = null,
    val vetAssessment: String? = null,
)
