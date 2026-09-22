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
 *  1. read symptom_reports where synced=0
 *  2. upload each report's local photo to B2 (if not already uploaded)
 *  3. POST the report (with remote photo URL) to the backend — stub for now
 *  4. on success set synced=1; on any failure leave synced=0 for the next cycle
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

            val pending = reportRepository.getUnsyncedReports()
            if (pending.isEmpty()) {
                SyncStatusHolder.onCycleCompleted(remaining = 0)
                return Result.success()
            }

            for (report in pending) {
                var current = report

                // Step 2: photo first, so the push carries the remote URL.
                // Never let a missing/failed photo block the report itself.
                val needsPhoto = current.photoLocalPath.isNotBlank() &&
                    current.photoRemoteUrl.isNullOrBlank()
                if (needsPhoto) {
                    when (val upload = b2UploadService.uploadReportPhoto(current)) {
                        is B2UploadService.UploadResult.Success -> {
                            current = current.copy(photoRemoteUrl = upload.fileUrl)
                        }
                        is B2UploadService.UploadResult.Skipped -> {
                            val fileExists = java.io.File(current.photoLocalPath).exists()
                            if (!fileExists) {
                                // Local photo gone — drop it and push the report anyway.
                                current = current.copy(photoLocalPath = "")
                            } else {
                                // Offline — wait for the next connected cycle.
                                continue
                            }
                        }
                        is B2UploadService.UploadResult.Failure -> {
                            Log.w(TAG, "Photo upload failed for ${report.id}: ${upload.message}")
                            // Push the report without the photo rather than wedging the queue.
                        }
                    }
                }

                // Step 3: push report to backend.
                when (val push = reportPushApi.pushReport(current)) {
                    is ReportPushApi.PushResult.Success -> {
                        reportRepository.update(current.copy(synced = true))
                    }
                    is ReportPushApi.PushResult.Failure -> {
                        // Leave synced=0 — retried next cycle.
                        Log.w(TAG, "Push failed for ${report.id}: ${push.message}")
                    }
                }
            }

            val remaining = reportRepository.getUnsyncedReports().size
            // Also push animals/vaccinations/alerts and pull remote → local.
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.syncAll() }
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
