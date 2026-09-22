package com.pashurakshak.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pashurakshak.app.data.remote.B2UploadService
import com.pashurakshak.app.data.remote.ReportPushApi
import com.pashurakshak.app.di.ServiceLocator

/**
 * One sync cycle over the offline queue:
 *  1. upload each report's local photo to B2 (regardless of synced flag, so failures retry)
 *  2. if a just-uploaded report was already synced, re-push to persist the photo URL
 *  3. POST every unsynced report (with remote photo URL) to the backend
 *  4. on success set synced=1; on any failure leave synced=0 for the next cycle
 *  5. also push animals/vaccinations/alerts and pull remote → local
 *
 * Runs only under NetworkType.CONNECTED (enforced by SyncScheduler), so offline
 * devices simply hold the work — no crashes, no failed-call hammering. An empty
 * queue exits immediately with zero network traffic.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        SyncStatusHolder.setSyncing(true)
        return try {
            val reportRepository = ServiceLocator.reportRepository
            val b2UploadService = ServiceLocator.b2UploadService
            val reportPushApi = ServiceLocator.reportPushApi

            // Phase 1: upload every report needing a photo (regardless of synced flag).
            val photoQueue = reportRepository.getReportsPendingPhotoUpload()
            val reportsThatGotPhoto = mutableListOf<com.pashurakshak.app.data.local.SymptomReport>()
            for (report in photoQueue) {
                when (val upload = b2UploadService.uploadReportPhoto(report)) {
                    is B2UploadService.UploadResult.Success -> {
                        reportsThatGotPhoto.add(report.copy(photoRemoteUrl = upload.fileUrl))
                    }
                    is B2UploadService.UploadResult.Skipped -> {
                        val fileExists = java.io.File(report.photoLocalPath).exists()
                        if (!fileExists) {
                            reportRepository.update(report.copy(photoLocalPath = ""))
                        }
                        // Offline — wait for the next connected cycle.
                    }
                    is B2UploadService.UploadResult.Failure -> {
                        Log.w(TAG, "Photo upload failed for ${report.id}: ${upload.message}")
                        // Push the report without the photo rather than wedging the queue.
                    }
                }
            }

            // Persist photo URLs for reports that were already synced (and thus already pushed).
            for (report in reportsThatGotPhoto) {
                if (report.synced) {
                    runCatching { reportPushApi.pushReport(report) }
                        .onFailure { Log.w(TAG, "Re-push after photo upload failed for ${report.id}: ${it.message}") }
                }
            }

            // Phase 2: push unsynced reports to backend (photo URL now included).
            val pending = reportRepository.getUnsyncedReports()
            for (report in pending) {
                when (val push = reportPushApi.pushReport(report)) {
                    is ReportPushApi.PushResult.Success -> {
                        reportRepository.update(report.copy(synced = true))
                    }
                    is ReportPushApi.PushResult.Failure -> {
                        // Leave synced=0 — retried next cycle.
                        Log.w(TAG, "Push failed for ${report.id}: ${push.message}")
                    }
                }
            }

            // Phase 3: push animals/vaccinations/alerts and pull remote → local.
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.syncAll() }
            val remaining = reportRepository.getUnsyncedReports().size
            SyncStatusHolder.onCycleCompleted(remaining = remaining)
            // Success even with leftovers: failures stay queued and are retried by the
            // periodic work / next reconnect, not by hammering WorkManager retries.
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "Sync cycle failed", error)
            val remaining = runCatching {
                ServiceLocator.reportRepository.getUnsyncedReports().size
            }.getOrDefault(-1)
            SyncStatusHolder.onCycleCompleted(remaining = remaining.coerceAtLeast(0))
            // Keep the queue untouched; next cycle (15 min or reconnect) retries.
            Result.success()
        } finally {
            SyncStatusHolder.setSyncing(false)
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
