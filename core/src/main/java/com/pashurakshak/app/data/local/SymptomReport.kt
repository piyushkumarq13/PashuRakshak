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
    val qrCodeId: String = "QR-" + UUID.randomUUID().toString().uppercase(),
    val diseaseDescription: String? = null,
    // Server is the source of truth: only a successful visits.js match sets
    // these, and the value is mirrored locally after pull. Never written from
    // the report push payload.
    val qrVerified: Boolean = false,
    val verifiedAt: Long? = null,
    val verifiedBy: String? = null,
) {
    /**
     * `photo_local_path` / `photo_remote_url` store up to MAX_PHOTOS entries
     * joined by NEWLINE_SEPARATOR (same columns on device and server, so no
     * schema migration). Remote entries stay index-aligned with local paths —
     * "" means "not uploaded yet".
     */
    val photoLocalPaths: List<String>
        get() = photoLocalPath.split(NEWLINE_SEPARATOR).filter { it.isNotBlank() }

    val photoRemoteUrls: List<String?>
        get() = photoRemoteUrl?.split(NEWLINE_SEPARATOR) ?: emptyList()

    val displayPhotoUrl: String?
        get() = photoRemoteUrls.firstOrNull { !it.isNullOrBlank() }

    val displayPhotoCount: Int
        get() = maxOf(photoLocalPaths.size, photoRemoteUrls.count { !it.isNullOrBlank() })

    companion object {
        const val NEWLINE_SEPARATOR = "\n"
        const val MAX_PHOTOS = 5

        fun joinPhotos(paths: List<String>): String =
            paths.joinToString(NEWLINE_SEPARATOR) { it.trim() }
    }
}
