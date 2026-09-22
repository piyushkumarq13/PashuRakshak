package com.pashurakshak.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log

/**
 * Fires an immediate sync as soon as the device regains connectivity.
 * Registered once from PashuRakshakApplication; enqueues unique one-shot work,
 * so a flapping connection can never stack duplicate sync jobs.
 */
object NetworkReconnectObserver {

    private const val TAG = "NetworkReconnectObserver"

    @Volatile
    private var registered = false

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun register(context: Context) {
        if (registered) return
        registered = true
        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — triggering immediate sync")
                SyncScheduler.triggerNow(appContext)
            }
        }
        callback = networkCallback

        // Default network: only the internet-capable network is reported, avoiding
        // duplicate callbacks for captive-portal / peer networks.
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (error: RuntimeException) {
            // Rare platform failure (e.g. registering too fast after boot) — periodic
            // work still covers us; never crash the app over a sync nicety.
            registered = false
            callback = null
            Log.w(TAG, "Failed to register network callback", error)
        }
    }
}
