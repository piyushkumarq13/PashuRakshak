package com.pashurakshak.app.data.sync

import com.pashurakshak.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Shared, app-wide view of the sync queue for the status banner. */
object SyncStatusHolder {

    data class SyncUiStatus(
        /** Reports with synced=0 still waiting to reach the backend. */
        val pendingCount: Int = 0,
        val isSyncing: Boolean = false,
        /** Epoch millis of the last completed sync cycle, if any. */
        val lastSyncAt: Long? = null,
    )

    private val _status = MutableStateFlow(SyncUiStatus())
    val status: StateFlow<SyncUiStatus> = _status.asStateFlow()

    /** Re-read the pending count from the local queue (safe to call from UI). */
    suspend fun refreshPendingCount() {
        val count = runCatching {
            ServiceLocator.reportRepository.getUnsyncedReports().size
        }.getOrDefault(_status.value.pendingCount)
        _status.update { it.copy(pendingCount = count) }
    }

    fun setSyncing(syncing: Boolean) {
        _status.update { it.copy(isSyncing = syncing) }
    }

    /** Called by SyncWorker at the end of every cycle with the remaining queue size. */
    fun onCycleCompleted(remaining: Int) {
        _status.update {
            it.copy(
                pendingCount = remaining,
                isSyncing = false,
                lastSyncAt = System.currentTimeMillis(),
            )
        }
    }
}
