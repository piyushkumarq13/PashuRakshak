package com.pashurakshak.app

import android.app.Application
import android.util.Log
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.sync.NetworkReconnectObserver
import com.pashurakshak.app.data.sync.SyncScheduler
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.notifications.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PashuRakshakApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SessionManager.initialize(this)
        ServiceLocator.initialize(this)
        AppNotifications.ensureChannel(this)
        // Offline sync engine: periodic every 15 min (network-constrained) + immediate
        // run whenever connectivity returns.
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.triggerNow(this)
        NetworkReconnectObserver.register(this)
        runDatabaseSmokeCheck()
        // Push local queue + pull remote data so every screen starts from the cloud.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { com.pashurakshak.app.data.sync.RemoteSync.syncAll() }
        }
    }

    // TEMPORARY: exercises schema + repositories at startup; remove once features consume the data layer.
    private fun runDatabaseSmokeCheck() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                ServiceLocator.animalRepository.getAll()
                ServiceLocator.reportRepository.getUnsyncedReports()
                ServiceLocator.vaccinationRepository.getAll()
                ServiceLocator.vetRepository.getAll()
                ServiceLocator.alertRepository.getAll()
                Log.i(TAG, "DB smoke check OK")
            }.onFailure {
                Log.e(TAG, "DB smoke check failed", it)
            }
        }
    }

    private companion object {
        const val TAG = "PashuRakshak"
    }
}
