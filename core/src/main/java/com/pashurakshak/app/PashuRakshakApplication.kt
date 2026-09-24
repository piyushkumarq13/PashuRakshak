package com.pashurakshak.app

import android.app.Application
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.sync.NetworkReconnectObserver
import com.pashurakshak.app.data.sync.SyncScheduler
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.notifications.AppNotifications

class PashuRakshakApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SessionManager.initialize(this)
        ServiceLocator.initialize(this)
        AppNotifications.ensureChannel(this)
        // Offline sync engine: periodic every 15 min (network-constrained) + immediate
        // run whenever connectivity returns. Screens load local-first and refresh
        // themselves, so we don't force a blocking sync here.
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.triggerNow(this)
        NetworkReconnectObserver.register(this)
    }
}
