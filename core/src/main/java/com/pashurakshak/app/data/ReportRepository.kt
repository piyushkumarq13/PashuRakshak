package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.ReportStatus
import com.pashurakshak.app.data.local.SymptomReport
import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.boolean
import com.pashurakshak.app.data.local.double
import com.pashurakshak.app.data.local.int
import com.pashurakshak.app.data.local.long
import com.pashurakshak.app.data.local.string
import com.pashurakshak.app.data.local.stringOrNull
import org.json.JSONArray
import org.json.JSONObject
import tech.turso.libsql.Row

class ReportRepository(private val db: TursoClient) {

    suspend fun insert(report: SymptomReport) {
        db.execute(
            """
            INSERT INTO symptom_reports (
                id, animal_id, farmer_id, symptoms, photo_local_path, photo_remote_url,
                latitude, longitude, risk_score, risk_breakdown, status, synced, created_at,
                assigned_vet_id, ai_risk_category, vet_assessment, village, qr_code_id, disease_description,
                qr_verified, verified_at, verified_by, resolved_at, resolved_by, resolution_note
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            report.id,
            report.animalId,
            report.farmerId,
            symptomsToJson(report.symptoms),
            report.photoLocalPath,
            report.photoRemoteUrl,
            report.latitude,
            report.longitude,
            report.riskScore,
            riskBreakdownToJson(report.riskBreakdown),
            report.status.dbValue,
            if (report.synced) 1 else 0,
            report.createdAt,
            report.assignedVetId ?: "",
            report.aiAdvisory ?: "",
            report.vetAssessment ?: "",
            report.village ?: "",
            report.qrCodeId,
            report.diseaseDescription ?: "",
            if (report.qrVerified) 1 else 0,
            report.verifiedAt,
            report.verifiedBy,
            report.resolvedAt,
            report.resolvedBy,
            report.resolutionNote,
        )
    }

    suspend fun getAll(): List<SymptomReport> =
        db.query("SELECT * FROM symptom_reports ORDER BY created_at DESC") { it.toReport() }

    suspend fun getById(id: String): SymptomReport? =
        db.query("SELECT * FROM symptom_reports WHERE id = ?", id) { it.toReport() }.firstOrNull()

    suspend fun getByAnimal(animalId: String): List<SymptomReport> =
        db.query(
            "SELECT * FROM symptom_reports WHERE animal_id = ? ORDER BY created_at DESC",
            animalId,
        ) { it.toReport() }

    /** Reports for a specific farmer, newest first. */
    suspend fun getByFarmer(farmerId: String): List<SymptomReport> =
        db.query(
            "SELECT * FROM symptom_reports WHERE farmer_id = ? ORDER BY created_at DESC",
            farmerId,
        ) { it.toReport() }

    /** Reports not yet pushed to the server — the offline sync queue (sync logic comes later). */
    suspend fun getUnsyncedReports(): List<SymptomReport> =
        db.query(
            "SELECT * FROM symptom_reports WHERE synced = 0 ORDER BY created_at ASC",
        ) { it.toReport() }

    suspend fun countUnsyncedReports(): Int =
        db.query("SELECT COUNT(*) FROM symptom_reports WHERE synced = 0") { it.int(0) }
            .firstOrNull() ?: 0

    /**
     * Reports with a local photo but no remote URL yet —
     * the B2 photo-upload queue (feeds into the sync flow).
     * Includes reports regardless of synced flag so retried failures eventually succeed.
     */
    suspend fun getReportsPendingPhotoUpload(): List<SymptomReport> =
        db.query(
            """
            SELECT * FROM symptom_reports
            WHERE photo_local_path != ''
              AND (photo_remote_url IS NULL OR photo_remote_url = '')
            ORDER BY created_at ASC
            """.trimIndent(),
        ) { it.toReport() }

    suspend fun update(report: SymptomReport) {
        db.execute(
            """
            UPDATE symptom_reports SET
                animal_id = ?, farmer_id = ?, symptoms = ?, photo_local_path = ?, photo_remote_url = ?,
                latitude = ?, longitude = ?, risk_score = ?, risk_breakdown = ?, status = ?,
                synced = ?, created_at = ?, assigned_vet_id = ?, ai_risk_category = ?,
                vet_assessment = ?, village = ?, qr_code_id = ?, disease_description = ?,
                qr_verified = ?, verified_at = ?, verified_by = ?,
                resolved_at = ?, resolved_by = ?, resolution_note = ?
            WHERE id = ?
            """.trimIndent(),
            report.animalId,
            report.farmerId,
            symptomsToJson(report.symptoms),
            report.photoLocalPath,
            report.photoRemoteUrl,
            report.latitude,
            report.longitude,
            report.riskScore,
            riskBreakdownToJson(report.riskBreakdown),
            report.status.dbValue,
            if (report.synced) 1 else 0,
            report.createdAt,
            report.assignedVetId ?: "",
            report.aiAdvisory ?: "",
            report.vetAssessment ?: "",
            report.village ?: "",
            report.qrCodeId,
            report.diseaseDescription ?: "",
            if (report.qrVerified) 1 else 0,
            report.verifiedAt,
            report.verifiedBy,
            report.resolvedAt,
            report.resolvedBy,
            report.resolutionNote,
            report.id,
        )
    }

    suspend fun delete(id: String) {
        db.execute("DELETE FROM symptom_reports WHERE id = ?", id)
    }

    suspend fun insertVisitVerification(
        reportId: String,
        scannedQrCodeId: String,
        latitude: Double,
        longitude: Double,
        assessment: String,
        matched: Int,
    ) {
        db.execute(
            """
            INSERT INTO pashu_visit_verifications (id, report_id, scanned_qr_code_id, latitude, longitude, assessment, matched, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            java.util.UUID.randomUUID().toString(),
            reportId,
            scannedQrCodeId,
            latitude,
            longitude,
            assessment,
            matched,
            System.currentTimeMillis(),
        )
    }

    /** True only when a verification row with matched = 1 exists for this report. */
    suspend fun hasVerifiedVisitRow(reportId: String): Boolean =
        db.query(
            "SELECT 1 FROM pashu_visit_verifications WHERE report_id = ? AND matched = 1 LIMIT 1",
            reportId,
        ) { it }.isNotEmpty()

    /** Upsert from remote pull — keeps local photo path and village if the row already exists. */
    suspend fun upsertFromRemote(report: SymptomReport) {
        val existing = getById(report.id)
        if (existing == null) {
            insert(report)
        } else if (!existing.synced) {
            // The local row has its own unpushed changes (e.g. the vet marked it
            // EXAMINED) — do not let a stale remote row overwrite them. The
            // pending push worker will reconcile both sides on its next cycle.
            update(
                existing.copy(
                    status = if (report.isResolved) ReportStatus.RESOLVED else existing.status,
                    photoRemoteUrl = report.photoRemoteUrl ?: existing.photoRemoteUrl,
                    // The server is the source of truth for QR verification and
                    // resolution even when the local row has pending push changes.
                    qrVerified = report.qrVerified,
                    verifiedAt = report.verifiedAt,
                    verifiedBy = report.verifiedBy,
                    resolvedAt = report.resolvedAt ?: existing.resolvedAt,
                    resolvedBy = report.resolvedBy ?: existing.resolvedBy,
                    resolutionNote = report.resolutionNote ?: existing.resolutionNote,
                ),
            )
        } else {
            update(
                report.copy(
                    photoLocalPath = existing.photoLocalPath
                        .ifBlank { report.photoLocalPath },
                    photoRemoteUrl = report.photoRemoteUrl
                        ?: existing.photoRemoteUrl,
                    village = existing.village ?: report.village,
                    aiAdvisory = existing.aiAdvisory ?: report.aiAdvisory,
                    vetAssessment = existing.vetAssessment ?: report.vetAssessment,
                    assignedVetId = existing.assignedVetId ?: report.assignedVetId,
                    qrCodeId = existing.qrCodeId.ifBlank { report.qrCodeId },
                    diseaseDescription = existing.diseaseDescription ?: report.diseaseDescription,
                    resolvedAt = report.resolvedAt ?: existing.resolvedAt,
                    resolvedBy = report.resolvedBy ?: existing.resolvedBy,
                    resolutionNote = report.resolutionNote ?: existing.resolutionNote,
                ),
            )
        }
    }

    // Column order must match the symptom_reports SELECT * order (after migration 004).
    private fun Row.toReport() = SymptomReport(
        id = string(0),
        animalId = string(1),
        farmerId = string(2),
        symptoms = jsonToSymptoms(string(3)),
        photoLocalPath = string(4),
        photoRemoteUrl = cleanRemoteString(stringOrNull(5)),
        latitude = double(6),
        longitude = double(7),
        riskScore = int(8),
        riskBreakdown = jsonToRiskBreakdown(string(9)),
        status = ReportStatus.fromDbValue(string(10)),
        synced = boolean(11),
        createdAt = long(12),
        assignedVetId = cleanRemoteString(stringOrNull(13)),
        aiAdvisory = cleanRemoteString(stringOrNull(14)),
        vetAssessment = cleanRemoteString(stringOrNull(15)),
        village = cleanRemoteString(stringOrNull(16)),
        qrCodeId = cleanRemoteString(stringOrNull(17)).orEmpty(),
        diseaseDescription = cleanRemoteString(stringOrNull(18)),
        qrVerified = int(19) != 0,
        verifiedAt = (this[20] as? Number)?.toLong(),
        verifiedBy = cleanRemoteString(stringOrNull(21)),
        resolvedAt = (this[22] as? Number)?.toLong(),
        resolvedBy = cleanRemoteString(stringOrNull(23)),
        resolutionNote = cleanRemoteString(stringOrNull(24)),
    )

    private fun symptomsToJson(symptoms: List<String>): String =
        JSONArray(symptoms).toString()

    private fun jsonToSymptoms(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun riskBreakdownToJson(breakdown: Map<String, Int>): String {
        val obj = JSONObject()
        breakdown.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    private fun jsonToRiskBreakdown(json: String): Map<String, Int> {
        val obj = JSONObject(json)
        return obj.keys().asSequence().associateWith { obj.getInt(it) }
    }
}
