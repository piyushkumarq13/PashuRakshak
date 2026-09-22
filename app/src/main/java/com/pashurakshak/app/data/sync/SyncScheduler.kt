package com.pashurakshak.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the offline sync engine:
 * - periodic sync every 15 minutes (minimum WorkManager interval), only on network
 * - immediate one-shot sync on demand (network reconnect / app start)
 *
 * Both use NetworkType.CONNECTED so WorkManager itself holds the work while offline —
 * no manual polling loops, no battery-hungry retry storms.
 */
object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "pashurakshak_periodic_sync"
    private const val IMMEDIATE_WORK_NAME = "pashurakshak_immediate_sync"
    private const val PERIODIC_INTERVAL_MINUTES = 15L

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /** Enqueue the recurring sync (idempotent — KEEP avoids duplicating or resetting it). */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Trigger a sync soon. Constrained to CONNECTED (runs when online, waits otherwise)
     * and KEEP so repeated reconnect callbacks never stack duplicate runs.
     */
    fun triggerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
